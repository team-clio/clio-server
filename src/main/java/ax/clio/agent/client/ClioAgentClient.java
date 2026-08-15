package ax.clio.agent.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Component
public class ClioAgentClient {
	private static final String ROOT_GRAPH_ID = "clio_agent";
	private static final String PROCESS_REPORT = "process_report";

	private final RestClient restClient;

	@Autowired
	public ClioAgentClient(ClioAgentProperties properties) {
		// LangGraph's local Uvicorn server accepts HTTP/1.1, not h2c upgrades.
		this(
				RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory()),
				properties
		);
	}

	ClioAgentClient(RestClient.Builder builder, ClioAgentProperties properties) {
		this.restClient = builder.baseUrl(properties.url().toString()).build();
	}

	public void processBug(Long projectId, Long bugId) {
		String body = """
				{"assistant_id":"%s","input":{"request":{"request_id":"process-bug-%d","request_type":"%s","project_id":"%d","payload":{"bug_id":"%d"}}}}
				""".formatted(ROOT_GRAPH_ID, bugId, PROCESS_REPORT, projectId, bugId);
		restClient.post()
				.uri("/runs")
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.toBodilessEntity();
	}
}
