package ax.clio.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LlmConfigServiceTest {

	private final LlmConfigRepository repository = Mockito.mock(LlmConfigRepository.class);
	private final LlmConfigService service = new LlmConfigService(repository);

	@Test
	void updateKeepsExistingEnabledDefaultAndApiKeyWhenRequestValuesAreNullOrBlank() {
		LlmConfig config = new LlmConfig(
				"openai",
				LlmProvider.OPENAI,
				"https://api.openai.com/v1",
				"sk-old",
				"gpt-old",
				false,
				true
		);
		when(repository.findById(1L)).thenReturn(Optional.of(config));

		LlmConfigResponse response = service.update(1L, new LlmConfigUpdateRequest(
				"openai-updated",
				LlmProvider.OPENAI,
				"https://api.openai.com/v1/",
				"",
				"gpt-new",
				null,
				null
		));

		assertThat(response.enabled()).isFalse();
		assertThat(response.defaultConfig()).isTrue();
		assertThat(config.getApiKey()).isEqualTo("sk-old");
		assertThat(config.getBaseUrl()).isEqualTo("https://api.openai.com/v1");
		assertThat(config.getDefaultModel()).isEqualTo("gpt-new");
		verify(repository, never()).findAll();
	}

	@Test
	void updateToDefaultClearsExistingDefaultConfig() {
		LlmConfig previousDefault = new LlmConfig(
				"previous",
				LlmProvider.OPENAI,
				"https://api.openai.com/v1",
				"sk-previous",
				"gpt-old",
				true,
				true
		);
		LlmConfig target = new LlmConfig(
				"target",
				LlmProvider.DEEPSEEK,
				"https://api.deepseek.com",
				"sk-target",
				"deepseek-chat",
				true,
				false
		);
		when(repository.findById(2L)).thenReturn(Optional.of(target));
		when(repository.findAll()).thenReturn(List.of(previousDefault, target));

		LlmConfigResponse response = service.update(2L, new LlmConfigUpdateRequest(
				"target",
				LlmProvider.DEEPSEEK,
				"https://api.deepseek.com",
				null,
				"deepseek-chat",
				true,
				true
		));

		assertThat(response.defaultConfig()).isTrue();
		assertThat(target.isDefaultConfig()).isTrue();
		assertThat(previousDefault.isDefaultConfig()).isFalse();
	}
}
