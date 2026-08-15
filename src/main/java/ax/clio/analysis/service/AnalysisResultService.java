package ax.clio.analysis.service;

import ax.clio.analysis.dto.AnalysisResultResponse;
import ax.clio.analysis.dto.LatestAnalysisResultResponse;
import ax.clio.analysis.dto.LatestIssueAnalysisResponse;
import ax.clio.analysis.dto.SaveAnalysisResultRequest;
import ax.clio.analysis.entity.AnalysisResult;
import ax.clio.analysis.entity.AnalysisResultStatus;
import ax.clio.analysis.repository.AnalysisResultRepository;
import ax.clio.common.ConflictException;
import ax.clio.common.ResourceNotFoundException;
import ax.clio.issue.entity.Issue;
import ax.clio.issue.repository.IssueRepository;
import ax.clio.issue.service.RiskPriorityMapper;
import ax.clio.workflow.entity.AgentWorkflowRun;
import ax.clio.workflow.entity.AgentWorkflowStatus;
import ax.clio.workflow.repository.AgentWorkflowRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AnalysisResultService {
	private static final com.fasterxml.jackson.databind.ObjectMapper PERSISTENCE_MAPPER =
			new com.fasterxml.jackson.databind.ObjectMapper();

	private final AnalysisResultRepository analysisResultRepository;
	private final AgentWorkflowRunRepository workflowRunRepository;
	private final IssueRepository issueRepository;
	private final ObjectMapper objectMapper;

	@Transactional
	public AnalysisResultResponse save(Long projectId, Long runId, SaveAnalysisResultRequest request) {
		AgentWorkflowRun run = workflowRunRepository.findByIdAndProjectId(runId, projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Workflow run not found: " + runId));
		if (run.getStatus() != AgentWorkflowStatus.RUNNING) {
			throw new ConflictException("Analysis result requires a RUNNING workflow: " + runId);
		}
		AnalysisResult replay = analysisResultRepository.findByWorkflowRunId(runId).orElse(null);
		if (replay != null) {
			Long replayPreviousId = replay.getPreviousAnalysisResult() == null
					? null
					: replay.getPreviousAnalysisResult().getId();
			if (!replay.getIssue().getId().equals(request.issueId())
					|| !java.util.Objects.equals(replayPreviousId, request.previousAnalysisResultId())
					|| !replay.getResultSnapshot().equals(persistenceTree(request.issueAnalysis()))) {
				throw new ConflictException("Workflow run already stored a different analysis result.");
			}
			return response(replay);
		}
		Issue issue = issueRepository.findByIdAndProjectId(request.issueId(), projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + request.issueId()));
		AnalysisResult previous = resolvePrevious(projectId, issue, request.previousAnalysisResultId());
		AnalysisResultStatus status = validateSnapshot(run, issue, previous, request.issueAnalysis());
		applyRiskAssessment(issue, request.issueAnalysis());
		return response(analysisResultRepository.save(AnalysisResult.create(
				run, issue, previous, status, persistenceTree(request.issueAnalysis())
		)));
	}

	@Transactional(readOnly = true)
	public LatestAnalysisResultResponse latest(Long projectId, Long issueId) {
		if (!issueRepository.existsByIdAndProjectId(issueId, projectId)) {
			throw new ResourceNotFoundException("Issue not found: " + issueId);
		}
		return analysisResultRepository.findFirstByIssueIdOrderByCreatedAtDesc(issueId)
				.map(result -> new LatestAnalysisResultResponse(
						result.getId(),
						result.getWorkflowRun().getId(),
						objectMapper.readTree(result.getResultSnapshot().toString())
				))
				.orElse(null);
	}

	@Transactional(readOnly = true)
	public LatestIssueAnalysisResponse latestForClient(Long projectId, Long issueId) {
		LatestAnalysisResultResponse result = latest(projectId, issueId);
		if (result == null) return null;
		return new LatestIssueAnalysisResponse(
				result.analysisResultId(), result.workflowRunId(), result.issueAnalysis()
		);
	}

	private AnalysisResult resolvePrevious(Long projectId, Issue issue, Long previousId) {
		if (previousId == null) return null;
		AnalysisResult previous = analysisResultRepository.findById(previousId)
				.orElseThrow(() -> new ResourceNotFoundException("Previous analysis result not found: " + previousId));
		if (!previous.getIssue().getId().equals(issue.getId())
				|| !previous.getIssue().getProject().getId().equals(projectId)) {
			throw new ConflictException("Previous analysis result belongs to a different Issue.");
		}
		return previous;
	}

	private AnalysisResultStatus validateSnapshot(
			AgentWorkflowRun run,
			Issue issue,
			AnalysisResult previous,
			JsonNode snapshot
	) {
		requireId(snapshot, "workflow_run_id", run.getId());
		requireId(snapshot, "project_id", run.getProject().getId());
		requireId(snapshot, "issue_id", issue.getId());
		JsonNode statusNode = snapshot.get("status");
		if (statusNode == null || !statusNode.isString()) {
			throw new ConflictException("IssueAnalysis status is required.");
		}
		AnalysisResultStatus status;
		try {
			status = AnalysisResultStatus.valueOf(statusNode.asText());
		} catch (IllegalArgumentException exception) {
			throw new ConflictException("Unsupported IssueAnalysis status: " + statusNode.asText());
		}
		JsonNode revision = snapshot.get("revision_summary");
		if (previous == null && revision != null && !revision.isNull()) {
			throw new ConflictException("Initial analysis must not contain revision_summary.");
		}
		if (previous != null) {
			if (revision == null || !revision.isObject()) {
				throw new ConflictException("Reanalysis requires revision_summary.");
			}
			requireId(revision, "previous_analysis_result_id", previous.getId());
		}
		return status;
	}

	private void applyRiskAssessment(Issue issue, JsonNode snapshot) {
		JsonNode risk = snapshot.get("risk_assessment");
		if (risk == null || risk.isNull()) {
			return;
		}
		if (!risk.isObject()) {
			throw new ConflictException("risk_assessment must be a JSON object.");
		}
		JsonNode scoreNode = risk.get("risk_score");
		if (scoreNode == null || !scoreNode.isIntegralNumber()) {
			throw new ConflictException("risk_assessment.risk_score must be an integer.");
		}
		int score = scoreNode.asInt();
		if (score < 0 || score > 100) {
			throw new ConflictException("risk_assessment.risk_score must be between 0 and 100.");
		}
		issue.applyRiskAssessment(score, RiskPriorityMapper.fromScore(score));
	}

	private void requireId(JsonNode snapshot, String field, Long expected) {
		JsonNode value = snapshot.get(field);
		if (value == null || !value.isIntegralNumber() || value.asLong() != expected) {
			throw new ConflictException(field + " must match the workflow context.");
		}
	}

	private com.fasterxml.jackson.databind.JsonNode persistenceTree(JsonNode snapshot) {
		try {
			return PERSISTENCE_MAPPER.readTree(snapshot.toString());
		} catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
			throw new IllegalStateException("IssueAnalysis snapshot cannot be persisted.", exception);
		}
	}

	private AnalysisResultResponse response(AnalysisResult result) {
		return new AnalysisResultResponse(
				result.getId(),
				result.getWorkflowRun().getId(),
				result.getIssue().getId(),
				result.getPreviousAnalysisResult() == null ? null : result.getPreviousAnalysisResult().getId(),
				result.getStatus()
		);
	}
}
