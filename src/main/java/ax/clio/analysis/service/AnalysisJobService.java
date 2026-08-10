package ax.clio.analysis.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import ax.clio.analysis.dto.AnalysisJobContextResponse;
import ax.clio.analysis.dto.AnalysisJobContextResponse.BugContext;
import ax.clio.analysis.dto.AnalysisJobContextResponse.IssueContext;
import ax.clio.analysis.dto.AnalysisJobResponse;
import ax.clio.analysis.dto.AnalysisResultResponse;
import ax.clio.analysis.dto.CreateAnalysisJobRequest;
import ax.clio.analysis.dto.SaveAnalysisResultRequest;
import ax.clio.analysis.dto.UpdateAnalysisJobRequest;
import ax.clio.analysis.entity.AnalysisJob;
import ax.clio.analysis.entity.AnalysisJobStatus;
import ax.clio.analysis.entity.AnalysisResult;
import ax.clio.analysis.entity.AnalysisResultStatus;
import ax.clio.analysis.repository.AnalysisJobRepository;
import ax.clio.analysis.repository.AnalysisResultRepository;
import ax.clio.bug.entity.Bug;
import ax.clio.bug.entity.BugOccurrence;
import ax.clio.bug.repository.BugOccurrenceRepository;
import ax.clio.bug.repository.BugRepository;
import ax.clio.common.ConflictException;
import ax.clio.common.ResourceNotFoundException;
import ax.clio.issue.entity.Issue;
import ax.clio.issue.entity.IssueBug;
import ax.clio.issue.repository.IssueBugRepository;
import ax.clio.issue.repository.IssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AnalysisJobService {

	private static final int MAX_CONTEXT_BUGS = 5;
	private static final com.fasterxml.jackson.databind.ObjectMapper SNAPSHOT_MAPPER =
			new com.fasterxml.jackson.databind.ObjectMapper();

	private final AnalysisJobRepository analysisJobRepository;
	private final AnalysisResultRepository analysisResultRepository;
	private final IssueRepository issueRepository;
	private final IssueBugRepository issueBugRepository;
	private final BugRepository bugRepository;
	private final BugOccurrenceRepository bugOccurrenceRepository;
	private final ObjectMapper objectMapper;

	@Transactional
	public AnalysisJobResponse create(
			Long projectId,
			Long issueId,
			CreateAnalysisJobRequest request
	) {
		Issue issue = findIssue(projectId, issueId);
		Bug triggerBug = bugRepository.findByIdAndProjectId(request.triggerBugId(), projectId)
				.orElseThrow(() -> new ResourceNotFoundException(
						"Trigger bug not found: " + request.triggerBugId()
				));
		if (!issueBugRepository.existsByIssueIdAndBugId(issueId, triggerBug.getId())) {
			throw new ConflictException("Trigger bug is not linked to issue: " + triggerBug.getId());
		}

		AnalysisJob previousJob = resolvePreviousJob(
				projectId,
				issueId,
				request.previousAnalysisJobId()
		);
		AnalysisJob saved = analysisJobRepository.save(
				AnalysisJob.create(issue.getProject(), triggerBug, issue, previousJob)
		);
		return AnalysisJobResponse.from(saved);
	}

	@Transactional(readOnly = true)
	public AnalysisJobContextResponse getContext(Long projectId, Long jobId) {
		AnalysisJob job = findJob(projectId, jobId);
		Issue issue = job.getIssue();
		List<BugContext> bugs = contextBugs(issue.getId(), job.getBug());
		JsonNode previousAnalysis = previousAnalysis(job);
		return new AnalysisJobContextResponse(
				job.getId(),
				projectId,
				new IssueContext(issue.getId(), issue.getTitle(), issue.getSummary()),
				job.getBug().getId(),
				bugs,
				previousAnalysis
		);
	}

	@Transactional
	public AnalysisJobResponse update(
			Long projectId,
			Long jobId,
			UpdateAnalysisJobRequest request
	) {
		AnalysisJob job = findJob(projectId, jobId);
		try {
			if (request.status() == AnalysisJobStatus.RUNNING) {
				job.start();
			} else if (request.status() == AnalysisJobStatus.FAILED) {
				job.fail(request.failureReason());
			} else {
				throw new ConflictException("Unsupported analysis job status transition.");
			}
		} catch (IllegalStateException | IllegalArgumentException exception) {
			throw new ConflictException(exception.getMessage());
		}
		return AnalysisJobResponse.from(job);
	}

	@Transactional
	public AnalysisResultResponse saveResult(
			Long projectId,
			Long jobId,
			SaveAnalysisResultRequest request
	) {
		AnalysisJob job = findJob(projectId, jobId);
		if (job.getStatus() != AnalysisJobStatus.RUNNING) {
			throw new ConflictException("Analysis result can only be saved for a RUNNING job.");
		}
		if (analysisResultRepository.existsByJobId(jobId)) {
			throw new ConflictException("Analysis result already exists for job: " + jobId);
		}

		JsonNode snapshot = request.issueAnalysis();
		AnalysisResultStatus resultStatus = validateSnapshot(job, snapshot);
		AnalysisResult result = analysisResultRepository.save(AnalysisResult.create(
				job,
				resultStatus,
				job.getPreviousAnalysisJob(),
				persistenceTree(snapshot)
		));
		job.complete();
		return AnalysisResultResponse.from(result);
	}

	private AnalysisJob resolvePreviousJob(Long projectId, Long issueId, Long previousJobId) {
		if (previousJobId == null) {
			return null;
		}
		AnalysisJob previous = analysisJobRepository.findByIdAndProjectIdAndIssueId(
				previousJobId,
				projectId,
				issueId
		).orElseThrow(() -> new ResourceNotFoundException(
				"Previous analysis job not found for issue: " + previousJobId
		));
		if (previous.getStatus() != AnalysisJobStatus.COMPLETED
				|| !analysisResultRepository.existsByJobId(previousJobId)) {
			throw new ConflictException("Previous analysis job must have a completed result.");
		}
		return previous;
	}

	private List<BugContext> contextBugs(Long issueId, Bug triggerBug) {
		List<IssueBug> linked = issueBugRepository.findByIssueIdOrderByBugOccurrenceCountDesc(issueId);
		Set<Long> orderedIds = new LinkedHashSet<>();
		orderedIds.add(triggerBug.getId());
		linked.forEach(link -> orderedIds.add(link.getBug().getId()));

		List<BugContext> result = new ArrayList<>();
		for (Long bugId : orderedIds) {
			if (result.size() == MAX_CONTEXT_BUGS) {
				break;
			}
			Bug bug = bugId.equals(triggerBug.getId())
					? triggerBug
					: linked.stream()
							.map(IssueBug::getBug)
							.filter(candidate -> candidate.getId().equals(bugId))
							.findFirst()
							.orElseThrow();
			BugOccurrence latest = bugOccurrenceRepository
					.findFirstByBugIdOrderByOccurredAtDesc(bugId)
					.orElseThrow(() -> new ResourceNotFoundException(
							"Bug report not found for bug: " + bugId
					));
			result.add(new BugContext(bugId, latest.getId(), bug.getOccurrenceCount()));
		}
		return List.copyOf(result);
	}

	private JsonNode previousAnalysis(AnalysisJob job) {
		if (job.getPreviousAnalysisJob() == null) {
			return null;
		}
		AnalysisResult previous = analysisResultRepository
				.findByJobId(job.getPreviousAnalysisJob().getId())
				.orElseThrow(() -> new ConflictException(
						"Previous analysis result is missing: " + job.getPreviousAnalysisJob().getId()
				));
		return objectMapper.readTree(previous.getResultSnapshot().toString());
	}

	private AnalysisResultStatus validateSnapshot(AnalysisJob job, JsonNode snapshot) {
		requireId(snapshot, "analysis_job_id", job.getId());
		requireId(snapshot, "project_id", job.getProject().getId());
		requireId(snapshot, "issue_id", job.getIssue().getId());

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
		if (job.getPreviousAnalysisJob() == null) {
			if (revision != null && !revision.isNull()) {
				throw new ConflictException("Initial analysis must not contain revision_summary.");
			}
		} else {
			if (revision == null || !revision.isObject()) {
				throw new ConflictException("Reanalysis requires revision_summary.");
			}
			requireId(
					revision,
					"previous_analysis_job_id",
					job.getPreviousAnalysisJob().getId()
			);
		}
		return status;
	}

	private void requireId(JsonNode snapshot, String field, Long expected) {
		JsonNode value = snapshot.get(field);
		if (value == null || !value.isIntegralNumber() || value.asLong() != expected) {
			throw new ConflictException(field + " must match the analysis job context.");
		}
	}

	private com.fasterxml.jackson.databind.JsonNode persistenceTree(JsonNode snapshot) {
		try {
			return SNAPSHOT_MAPPER.readTree(snapshot.toString());
		} catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
			throw new IllegalStateException("IssueAnalysis snapshot cannot be persisted.", exception);
		}
	}

	private AnalysisJob findJob(Long projectId, Long jobId) {
		return analysisJobRepository.findByIdAndProjectId(jobId, projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Analysis job not found: " + jobId));
	}

	private Issue findIssue(Long projectId, Long issueId) {
		return issueRepository.findByIdAndProjectId(issueId, projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueId));
	}
}
