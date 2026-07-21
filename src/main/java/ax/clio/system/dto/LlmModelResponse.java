package ax.clio.system.dto;

public record LlmModelResponse(
		String name,
		String providerType,
		String purpose,
		Integer contextWindow,
		Integer embeddingDimension
) {
}
