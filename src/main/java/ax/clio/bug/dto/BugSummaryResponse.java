package ax.clio.bug.dto;

import java.time.Instant;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BugSummaryResponse(
		Long id,
		Long projectId,
		Long issueId,
		String title,
		String source,
		String errorType,
		String topStackFrame,
		String status,
		String severity,
		Instant occurredAt,
		Instant createdAt
) {
}
