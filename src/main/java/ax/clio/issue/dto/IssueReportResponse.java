package ax.clio.issue.dto;

import java.time.Instant;

public record IssueReportResponse(
		Long id,
		String title,
		String source,
		String groupedBy,
		Double confidence,
		Instant occurredAt
) {
}
