package ax.clio.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ax.clio.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleEmbeddingClientTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void parsesEmbeddingVectorFromOpenAiShape() {
		String response = """
				{"data":[{"embedding":[0.1, -0.2, 0.3]}],"model":"text-embedding-3-small"}
				""";

		float[] vector = OpenAiCompatibleEmbeddingClient.parseEmbedding(objectMapper, response);

		assertThat(vector).containsExactly(0.1f, -0.2f, 0.3f);
	}

	@Test
	void throwsWhenNoVectorPresent() {
		assertThatThrownBy(() -> OpenAiCompatibleEmbeddingClient.parseEmbedding(objectMapper, "{\"data\":[]}"))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void throwsWhenResponseUnparseable() {
		assertThatThrownBy(() -> OpenAiCompatibleEmbeddingClient.parseEmbedding(objectMapper, "not-json"))
				.isInstanceOf(BusinessException.class);
	}
}
