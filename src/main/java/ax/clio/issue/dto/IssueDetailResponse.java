package ax.clio.issue.dto;

import java.time.Instant;
import java.util.List;

public record IssueDetailResponse(
		Long id,
		Long projectId,
		String title,
		String summary,
		String status,
		String priority,
		String severity,
		String assigneeName,
		Double aiConfidence,
		Integer bugCount,
		Integer importanceScore,
		Integer riskScore,
		Instant firstSeenAt,
		Instant lastSeenAt,
		List<IssueBugResponse> bugs
) {
}
