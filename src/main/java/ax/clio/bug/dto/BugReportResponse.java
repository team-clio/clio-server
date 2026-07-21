package ax.clio.bug.dto;

import java.time.Instant;

public record BugReportResponse(
		Long id,
		Long projectId,
		Long issueId,
		String title,
		String source,
		String fingerprint,
		String errorType,
		String topApplicationFrame,
		String status,
		Instant occurredAt,
		Instant collectedAt,
		GroupingResponse grouping
) {
}
