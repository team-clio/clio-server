package ax.clio.agent.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

	public void deleteProjectData(Long projectId) {
		Map<String, Object> body = Map.of(
				"assistant_id", ROOT_GRAPH_ID,
				"input", Map.of("request", Map.of(
						"request_id", "project-deleted-" + projectId,
						"request_type", "project_deleted",
						"project_id", projectId.toString(),
						"payload", Map.of()
				))
		);
		restClient.post().uri("/runs/wait").contentType(MediaType.APPLICATION_JSON)
				.body(body).retrieve().toBodilessEntity();
	}

	public void dispatchRepositorySync(
			Long projectId,
			Long sourceId,
			RepositorySyncRequestType requestType,
			String requestId,
			String branch,
			String sourceUri,
			List<String> includePaths,
			List<String> excludePaths
	) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("repository_id", sourceId.toString());
		payload.put("branch", branch);
		if (sourceUri != null) {
			payload.put("source_uri", sourceUri);
		}
		if (requestType == RepositorySyncRequestType.REPOSITORY_ADDED) {
			payload.put("include_paths", List.copyOf(includePaths));
			payload.put("exclude_paths", List.copyOf(excludePaths));
		}
		Map<String, Object> body = Map.of(
				"assistant_id", ROOT_GRAPH_ID,
				"input", Map.of("request", Map.of(
						"request_id", requestId,
						"request_type", requestType.wireName(),
						"project_id", projectId.toString(),
						"payload", payload
				))
		);
		restClient.post()
				.uri("/runs")
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.toBodilessEntity();
	}

	public void syncDocument(
			Long projectId,
			Long documentId,
			DocumentSyncRequestType requestType,
			String title,
			String markdown,
			Map<String, Object> sourceMetadata
	) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("document_id", documentId.toString());
		payload.put("revision", "1");
		if (requestType == DocumentSyncRequestType.DOCUMENT_ADDED) {
			payload.put("title", title);
			payload.put("markdown", markdown);
			payload.put("source_metadata", sourceMetadata);
		}
		Map<String, Object> body = Map.of(
				"assistant_id", ROOT_GRAPH_ID,
				"input", Map.of("request", Map.of(
						"request_id", "document-" + requestType.wireName() + "-" + projectId + "-" + documentId,
						"request_type", requestType.wireName(),
						"project_id", projectId.toString(),
						"payload", payload
				))
		);
		restClient.post()
				.uri("/runs/wait")
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.toBodilessEntity();
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> readCodeEvidence(Long projectId, List<Map<String, Object>> citations) {
		Map<String, Object> body = Map.of(
				"assistant_id", ROOT_GRAPH_ID,
				"input", Map.of("request", Map.of(
						"request_id", "code-evidence-" + projectId,
						"request_type", "read_code_evidence",
						"project_id", projectId.toString(),
						"payload", Map.of("citations", citations)
				))
		);
		Map<String, Object> response = restClient.post()
				.uri("/runs/wait")
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(Map.class);
		return response == null ? Map.of() : response;
	}
}
