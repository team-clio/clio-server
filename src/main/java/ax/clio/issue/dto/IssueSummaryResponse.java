package ax.clio.issue.dto;

import java.time.Instant;

public record IssueSummaryResponse(
		Long id,
		Long projectId,
		String title,
		String summary,
		String status,
		String priority,
		String severity,
		Integer importanceScore,
		Integer riskScore,
		Integer reportCount,
		Instant firstSeenAt,
		Instant lastSeenAt,
		Instant updatedAt
) {
}
