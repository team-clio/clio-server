package ax.clio.llm;

import java.util.List;
import java.util.Map;

import ax.clio.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

	private final RestClient restClient;

	public OpenAiCompatibleLlmClient(RestClient.Builder restClientBuilder) {
		this.restClient = restClientBuilder.build();
	}

	@Override
	public String completeJson(LlmConfig config, String model, String systemPrompt, String userPrompt) {
		if (!supports(config.getProvider())) {
			throw new BusinessException(HttpStatus.BAD_REQUEST,
					"Provider is not supported yet: " + config.getProvider());
		}
		Map<String, Object> body = Map.of(
				"model", model,
				"temperature", 0,
				"response_format", Map.of("type", "json_object"),
				"messages", List.of(
						Map.of("role", "system", "content", systemPrompt),
						Map.of("role", "user", "content", userPrompt)
				)
		);
		return restClient.post()
				.uri(config.getBaseUrl() + "/chat/completions")
				.header("Authorization", "Bearer " + config.getApiKey())
				.body(body)
				.retrieve()
				.body(String.class);
	}

	private boolean supports(LlmProvider provider) {
		return provider == LlmProvider.OPENAI
				|| provider == LlmProvider.DEEPSEEK
				|| provider == LlmProvider.OPENAI_COMPATIBLE;
	}
}
