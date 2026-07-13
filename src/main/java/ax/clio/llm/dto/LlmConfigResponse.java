package ax.clio.llm.dto;

import ax.clio.llm.entity.LlmConfig;
import ax.clio.llm.entity.LlmProvider;

import java.time.Instant;

public record LlmConfigResponse(
		Long id,
		String name,
		LlmProvider provider,
		String baseUrl,
		String defaultModel,
		boolean enabled,
		boolean defaultConfig,
		boolean configured,
		String maskedApiKey,
		Instant createdAt,
		Instant updatedAt
) {

	public static LlmConfigResponse from(LlmConfig config) {
		return new LlmConfigResponse(
				config.getId(),
				config.getName(),
				config.getProvider(),
				config.getBaseUrl(),
				config.getDefaultModel(),
				config.isEnabled(),
				config.isDefaultConfig(),
				config.getApiKey() != null && !config.getApiKey().isBlank(),
				mask(config.getApiKey()),
				config.getCreatedAt(),
				config.getUpdatedAt()
		);
	}

	private static String mask(String apiKey) {
		if (apiKey == null || apiKey.isBlank()) {
			return null;
		}
		if (apiKey.length() <= 8) {
			return "****";
		}
		return apiKey.substring(0, Math.min(4, apiKey.length())) + "..." + apiKey.substring(apiKey.length() - 4);
	}
}
