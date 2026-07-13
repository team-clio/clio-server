package ax.clio.llm.dto;

import ax.clio.llm.entity.LlmConfig;
import ax.clio.llm.entity.LlmProvider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LlmConfigResponseTest {

	@Test
	void masksApiKeyInResponse() {
		LlmConfig config = new LlmConfig(
				"openai",
				LlmProvider.OPENAI,
				"https://api.openai.com/v1",
				"sk-1234567890",
				"gpt-test",
				true,
				true
		);

		LlmConfigResponse response = LlmConfigResponse.from(config);

		assertThat(response.configured()).isTrue();
		assertThat(response.maskedApiKey()).isEqualTo("sk-1...7890");
	}

	@Test
	void masksShortApiKey() {
		LlmConfig config = new LlmConfig(
				"local",
				LlmProvider.OPENAI_COMPATIBLE,
				"http://localhost:11434/v1",
				"short",
				"local-model",
				true,
				false
		);

		LlmConfigResponse response = LlmConfigResponse.from(config);

		assertThat(response.configured()).isTrue();
		assertThat(response.maskedApiKey()).isEqualTo("****");
	}
}
