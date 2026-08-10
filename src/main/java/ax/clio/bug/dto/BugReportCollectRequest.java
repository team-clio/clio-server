package ax.clio.bug.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public record BugReportCollectRequest(
		@Size(max = 200) String title,
		String description,
		@NotBlank String source,
		@Size(max = 255) String errorType,
		String message,
		List<String> stackTrace,
		@NotNull Instant occurredAt,
		JsonNode rawPayload
) {
	public BugReportCollectRequest {
		stackTrace = stackTrace == null ? List.of() : List.copyOf(stackTrace);
	}

	@JsonIgnore
	@AssertTrue(message = "Bug report must contain report content.")
	public boolean hasReportContent() {
		return hasText(title)
				|| hasText(description)
				|| hasText(errorType)
				|| hasText(message)
				|| !stackTrace.isEmpty()
				|| (rawPayload != null && !rawPayload.isEmpty());
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
