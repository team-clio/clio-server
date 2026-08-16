package ax.clio.agent.client;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import ax.clio.common.ResourceNotFoundException;

/**
 * 에이전트의 standalone PCM inspect API(별도 uvicorn 프로세스)를 읽기 전용으로 호출한다.
 *
 * <p>Spring은 PCM 데이터를 해석하지 않고 JSON을 그대로 중계하므로 응답은 Map으로 전달한다.
 */
@Component
public class PcmInspectClient {

	private final RestClient restClient;

	@Autowired
	public PcmInspectClient(PcmInspectProperties properties) {
		// LangGraph와 동일하게 Python 서버는 HTTP/1.1만 받으므로 h2c 업그레이드를 끈다.
		this(
				RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory()),
				properties
		);
	}

	PcmInspectClient(RestClient.Builder builder, PcmInspectProperties properties) {
		this.restClient = builder.baseUrl(properties.url().toString()).build();
	}

	public Map<String, Object> readSnapshot(String projectId) {
		return getObject("/pcm/projects/" + projectId + "/snapshot");
	}

	public List<Map<String, Object>> listKnowledge(String projectId) {
		return getList("/pcm/projects/" + projectId + "/knowledge");
	}

	public Map<String, Object> readKnowledge(String projectId, String knowledgeId) {
		return getObject("/pcm/projects/" + projectId + "/knowledge/" + knowledgeId);
	}

	private Map<String, Object> getObject(String path) {
		try {
			Map<String, Object> body = restClient.get()
					.uri(path)
					.retrieve()
					.body(Map.class);
			return body == null ? Map.of() : body;
		} catch (HttpClientErrorException.NotFound exception) {
			throw new ResourceNotFoundException("PCM resource not found: " + path);
		}
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> getList(String path) {
		try {
			List<Map<String, Object>> body = restClient.get()
					.uri(path)
					.retrieve()
					.body(List.class);
			return body == null ? List.of() : body;
		} catch (HttpClientErrorException.NotFound exception) {
			throw new ResourceNotFoundException("PCM resource not found: " + path);
		}
	}
}
