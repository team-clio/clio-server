package ax.clio.agent.client;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Component
public class ClioAgentClient {
	private static final String ROOT_GRAPH_ID = "clio_agent";
	private static final String PROCESS_REPORT = "process_report";

	private final RestClient restClient;

	@Autowired
	public ClioAgentClient(ClioAgentProperties properties) {
		this(RestClient.builder(), properties);
	}

	ClioAgentClient(RestClient.Builder builder, ClioAgentProperties properties) {
		this.restClient = builder.baseUrl(properties.url().toString()).build();
	}

	public void processBug(Long projectId, Long bugId) {
		GraphRequest graphRequest = new GraphRequest(
				"process-bug-" + bugId,
				PROCESS_REPORT,
				projectId.toString(),
				Map.of("bug_id", bugId.toString())
		);
		restClient.post()
				.uri("/runs")
				.body(new RunRequest(ROOT_GRAPH_ID, Map.of("request", graphRequest)))
				.retrieve()
				.toBodilessEntity();
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	private record RunRequest(
			String assistantId,
			Map<String, GraphRequest> input
	) {
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	private record GraphRequest(
			String requestId,
			String requestType,
			String projectId,
			Map<String, String> payload
	) {
	}
}
