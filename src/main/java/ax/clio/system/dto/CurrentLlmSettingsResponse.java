package ax.clio.system.dto;

public record CurrentLlmSettingsResponse(
		String providerType,
		String providerName,
		String baseUrl,
		String chatModel,
		String embeddingModel,
		boolean apiKeyConfigured,
		boolean enabled
) {
}
