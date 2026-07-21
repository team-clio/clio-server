package ax.clio.system.dto;

public record UpdateLlmModelsRequest(
		String chatModel,
		String embeddingModel
) {
}
