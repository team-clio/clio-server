package ax.clio.agent.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class ClioAgentClient {
	private static final String ROOT_GRAPH_ID = "clio_agent";
	private static final String PROCESS_REPORT = "process_report";

	private final RestClient restClient;
	private final Supplier<Map<String, String>> traceEnvelope;

	@Autowired
	public ClioAgentClient(
			RestClient.Builder builder,
			ClioAgentProperties properties,
			TraceEnvelopeFactory traceEnvelopeFactory
	) {
		// LangGraph's local Uvicorn server accepts HTTP/1.1, not h2c upgrades.
		this(
				builder.clone().requestFactory(new SimpleClientHttpRequestFactory()),
				properties,
				traceEnvelopeFactory::current
		);
	}

	ClioAgentClient(RestClient.Builder builder, ClioAgentProperties properties) {
		this(builder, properties, Map::of);
	}

	ClioAgentClient(
			RestClient.Builder builder,
			ClioAgentProperties properties,
			Supplier<Map<String, String>> traceEnvelope
	) {
		this.restClient = builder.baseUrl(properties.url().toString()).build();
		this.traceEnvelope = traceEnvelope;
	}

	public void processBug(Long projectId, Long bugId) {
		Map<String, Object> request = Map.of(
				"request_id", "process-bug-" + bugId,
				"request_type", PROCESS_REPORT,
				"project_id", projectId.toString(),
				"payload", Map.of("bug_id", bugId.toString())
		);
		restClient.post()
				.uri("/runs")
				.contentType(MediaType.APPLICATION_JSON)
				.body(runBody(request))
				.retrieve()
				.toBodilessEntity();
	}

	public void deleteProjectData(Long projectId) {
		Map<String, Object> request = Map.of(
						"request_id", "project-deleted-" + projectId,
						"request_type", "project_deleted",
						"project_id", projectId.toString(),
						"payload", Map.of()
		);
		restClient.post().uri("/runs/wait").contentType(MediaType.APPLICATION_JSON)
				.body(runBody(request)).retrieve().toBodilessEntity();
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
		Map<String, Object> request = Map.of(
						"request_id", requestId,
						"request_type", requestType.wireName(),
						"project_id", projectId.toString(),
						"payload", payload
		);
		restClient.post()
				.uri("/runs")
				.contentType(MediaType.APPLICATION_JSON)
				.body(runBody(request))
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
		Map<String, Object> request = Map.of(
						"request_id", "document-" + requestType.wireName() + "-" + projectId + "-" + documentId,
						"request_type", requestType.wireName(),
						"project_id", projectId.toString(),
						"payload", payload
		);
		restClient.post()
				.uri("/runs/wait")
				.contentType(MediaType.APPLICATION_JSON)
				.body(runBody(request))
				.retrieve()
				.toBodilessEntity();
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> readCodeEvidence(Long projectId, List<Map<String, Object>> citations) {
		Map<String, Object> request = Map.of(
						"request_id", "code-evidence-" + projectId,
						"request_type", "read_code_evidence",
						"project_id", projectId.toString(),
						"payload", Map.of("citations", citations)
		);
		Map<String, Object> response = restClient.post()
				.uri("/runs/wait")
				.contentType(MediaType.APPLICATION_JSON)
				.body(runBody(request))
				.retrieve()
				.body(Map.class);
		return response == null ? Map.of() : response;
	}

	private Map<String, Object> runBody(Map<String, Object> request) {
		Map<String, Object> input = new LinkedHashMap<>();
		input.put("request", request);
		Map<String, String> telemetry = traceEnvelope.get();
		if (!telemetry.isEmpty()) {
			input.put("telemetry", telemetry);
		}
		return Map.of("assistant_id", ROOT_GRAPH_ID, "input", input);
	}
}
