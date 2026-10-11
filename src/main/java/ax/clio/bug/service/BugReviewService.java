package ax.clio.bug.service;

import java.util.List;

import ax.clio.bug.dto.BugReviewResponse;
import ax.clio.bug.entity.Bug;
import ax.clio.bug.entity.BugStatus;
import ax.clio.bug.repository.BugRepository;
import ax.clio.issue.entity.Issue;
import ax.clio.issue.repository.IssueRepository;
import ax.clio.workflow.entity.AgentWorkflowRun;
import ax.clio.workflow.repository.AgentWorkflowRunRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BugReviewService {

	private final BugRepository bugRepository;
	private final AgentWorkflowRunRepository workflowRunRepository;
	private final IssueRepository issueRepository;

	@Transactional(readOnly = true)
	public List<BugReviewResponse> list(Long projectId) {
		return bugRepository.findAllByProjectIdAndStatusOrderByIdAsc(projectId, BugStatus.NEEDS_REVIEW).stream()
				.map(bug -> response(projectId, bug))
				.toList();
	}

	/**
	 * 검토 대기 버그의 가장 최근 처리 결과에서 Agent가 제안한 후보와 근거를 읽는다.
	 */
	@Transactional(readOnly = true)
	public BugReviewResponse get(Long projectId, Bug bug) {
		return response(projectId, bug);
	}

	private BugReviewResponse response(Long projectId, Bug bug) {
		AgentWorkflowRun run = workflowRunRepository
				.findAttemptsLatestFirst(projectId, "process-bug-" + bug.getId()).stream()
				.findFirst()
				.orElse(null);
		JsonNode result = run == null ? null : run.getResultSnapshot();
		Long candidateIssueId = longOrNull(result, "candidate_issue_id");
		Issue candidate = candidateIssueId == null
				? null
				: issueRepository.findByIdAndProjectId(candidateIssueId, projectId).orElse(null);
		return new BugReviewResponse(
				bug.getId(),
				bug.getTitle(),
				bug.getSource().name(),
				bug.getErrorType(),
				bug.getOccurredAt(),
				candidate == null ? null : candidate.getId(),
				candidate == null ? null : candidate.getTitle(),
				candidate == null ? null : candidate.getStatus().name(),
				result == null || !result.path("confidence").isNumber()
						? null
						: result.path("confidence").decimalValue(),
				result == null || !result.path("reason").isTextual() ? null : result.path("reason").asText(),
				run == null ? null : run.getId()
		);
	}

	private static Long longOrNull(JsonNode node, String field) {
		if (node == null) {
			return null;
		}
		JsonNode value = node.path(field);
		if (value.isNumber()) {
			return value.asLong();
		}
		if (value.isTextual() && value.asText().matches("[1-9]\\d*")) {
			return Long.parseLong(value.asText());
		}
		return null;
	}
}
