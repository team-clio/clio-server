package ax.clio.memory;

import java.util.Map;

import ax.clio.common.BusinessException;
import ax.clio.llm.LlmConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * OpenAI호환 {@code /embeddings} 실제 API 구현 (D3 옵션). 기존 {@link ax.clio.llm.LlmClient} 구현을 미러링한다.
 *
 * <p>차원이 모델에 따라 달라 {@link EmbeddingClient}의 고정 {@code dimensions()}에 바로 못 맞으므로,
 * 여기서는 (config, model, text) → 벡터의 저수준 호출만 제공한다. 활성 빈 자동 선택·embedding 모델 정책
 * 배선은 D3-1 후속에서 이 클라이언트를 {@link EmbeddingClient}로 어댑트한다.
 */
@Component
public class OpenAiCompatibleEmbeddingClient {

	private final RestClient restClient;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public OpenAiCompatibleEmbeddingClient(RestClient.Builder restClientBuilder) {
		this.restClient = restClientBuilder.build();
	}

	public float[] embed(LlmConfig config, String model, String text) {
		Map<String, Object> body = Map.of(
				"model", model,
				"input", text
		);
		String response = restClient.post()
				.uri(config.getBaseUrl() + "/embeddings")
				.header("Authorization", "Bearer " + config.getApiKey())
				.body(body)
				.retrieve()
				.body(String.class);
		return parseEmbedding(objectMapper, response);
	}

	static float[] parseEmbedding(ObjectMapper objectMapper, String response) {
		try {
			JsonNode embedding = objectMapper.readTree(response).path("data").path(0).path("embedding");
			if (!embedding.isArray() || embedding.isEmpty()) {
				throw new BusinessException(HttpStatus.BAD_GATEWAY, "Embedding response has no vector");
			}
			float[] vector = new float[embedding.size()];
			for (int i = 0; i < embedding.size(); i++) {
				vector[i] = (float) embedding.get(i).asDouble();
			}
			return vector;
		} catch (BusinessException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new BusinessException(HttpStatus.BAD_GATEWAY, "Embedding response cannot be parsed");
		}
	}
}
