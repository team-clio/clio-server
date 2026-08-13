package ax.clio.bug.dto;

import java.time.Instant;
import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentBugResponse(
		Long bugId,
		Long projectId,
		String title,
		String description,
		String source,
		String errorType,
		String message,
		List<String> stackTrace,
		Instant occurredAt,
		JsonNode rawPayload
) {
}
