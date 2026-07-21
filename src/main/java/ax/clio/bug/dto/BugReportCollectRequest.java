package ax.clio.bug.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public record BugReportCollectRequest(
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
