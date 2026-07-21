package ax.clio.system.dto;

public record LlmProviderResponse(
		String type,
		String name,
		String defaultBaseUrl,
		boolean requiresApiKey
) {
}
