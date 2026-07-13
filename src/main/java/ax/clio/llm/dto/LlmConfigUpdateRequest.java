package ax.clio.llm.dto;

import ax.clio.llm.entity.LlmProvider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LlmConfigUpdateRequest(
		@NotBlank
		String name,
		@NotNull
		LlmProvider provider,
		@NotBlank
		String baseUrl,
		String apiKey,
		@NotBlank
		String defaultModel,
		Boolean enabled,
		Boolean defaultConfig
) {
}
