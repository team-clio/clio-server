package ax.clio.issue.dto;

import java.time.Instant;

public record IssueBugResponse(
		Long id,
		String title,
		String source,
		String errorType,
		String topStackFrame,
		String groupedBy,
		Double confidence,
		Instant occurredAt
) {
}
