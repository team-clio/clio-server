package ax.clio.bug.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BugCollectRequest(
		@NotBlank @Size(max = 50) String source,
		@Size(max = 200) String title,
		String description,
		@Size(max = 255) String errorType,
		String message,
		List<@NotBlank String> stackTrace,
		JsonNode rawPayload,
		@NotNull Instant occurredAt
) {
	public BugCollectRequest {
		stackTrace = stackTrace == null ? List.of() : List.copyOf(stackTrace);
	}
}
