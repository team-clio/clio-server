package ax.clio.bug.dto;

import java.time.Instant;

public record BugReportSummaryResponse(
		Long id,
		Long projectId,
		Long issueId,
		String title,
		String source,
		String errorType,
		String topApplicationFrame,
		String status,
		String severity,
		Instant occurredAt,
		Instant collectedAt
) {
}
