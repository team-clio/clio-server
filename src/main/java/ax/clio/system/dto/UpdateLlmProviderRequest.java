package ax.clio.system.dto;

public record UpdateLlmProviderRequest(
		String providerType,
		String baseUrl,
		String apiKey
) {
}
