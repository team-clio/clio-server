package ax.clio.bug.dto;

import java.math.BigDecimal;
import java.time.Instant;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 매칭이 확신하지 못해 사람의 연결 판단을 기다리는 버그와 Agent가 제안한 후보 Issue.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BugReviewResponse(
		Long bugId,
		String title,
		String source,
		String errorType,
		Instant occurredAt,
		Long candidateIssueId,
		String candidateIssueTitle,
		String candidateIssueStatus,
		BigDecimal confidence,
		String reason,
		Long workflowRunId
) {
}
