package ax.clio.system.dto;

public record LlmConnectionTestResponse(
		boolean success,
		Long latencyMs,
		String message
) {
}
