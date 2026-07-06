package ax.clio.llm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LlmConfigCreateRequest(
		@NotBlank
		String name,
		@NotNull
		LlmProvider provider,
		@NotBlank
		String baseUrl,
		@NotBlank
		String apiKey,
		@NotBlank
		String defaultModel,
		Boolean enabled,
		Boolean defaultConfig
) {
}
