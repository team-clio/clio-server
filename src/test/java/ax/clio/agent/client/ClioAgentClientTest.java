package ax.clio.agent.client;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ClioAgentClientTest {

	@Test
	void includesTraceEnvelopeOutsideTheBusinessRequest() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ClioAgentClient client = new ClioAgentClient(
				builder,
				new ClioAgentProperties(true, URI.create("http://agent:2024")),
				() -> Map.of(
						"traceparent",
						"00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
				)
		);
		server.expect(once(), requestTo("http://agent:2024/runs"))
				.andExpect(content().json("""
						{
						  "assistant_id": "clio_agent",
						  "input": {
						    "request": {
						      "request_id": "process-bug-72",
						      "request_type": "process_report",
						      "project_id": "3",
						      "payload": {"bug_id": "72"}
						    },
						    "telemetry": {
						      "traceparent": "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
						    }
						  }
						}
						"""))
				.andRespond(withSuccess("{\"run_id\":\"run-1\"}", MediaType.APPLICATION_JSON));

		client.processBug(3L, 72L);

		server.verify();
	}

	@Test
	void dispatchesProcessReportToTheRootGraph() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ClioAgentClient client = new ClioAgentClient(
				builder,
				new ClioAgentProperties(true, URI.create("http://agent:2024"))
		);
		server.expect(once(), requestTo("http://agent:2024/runs"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
				.andExpect(content().json("""
						{
						  "assistant_id": "clio_agent",
						  "input": {
						    "request": {
						      "request_id": "process-bug-72",
						      "request_type": "process_report",
						      "project_id": "3",
						      "payload": {"bug_id": "72"}
						    }
						  }
						}
						"""))
				.andRespond(withSuccess("{\"run_id\":\"run-1\"}", MediaType.APPLICATION_JSON));

		client.processBug(3L, 72L);

		server.verify();
	}

	@Test
	void dispatchesRepositoryAddedToTheRootGraph() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ClioAgentClient client = new ClioAgentClient(
				builder,
				new ClioAgentProperties(true, URI.create("http://agent:2024"))
		);
		server.expect(once(), requestTo("http://agent:2024/runs"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
				.andExpect(content().json("""
						{
						  "assistant_id": "clio_agent",
						  "input": {
						    "request": {
						      "request_id": "repository-7-42-sync-1",
						      "request_type": "repository_added",
						      "project_id": "7",
						      "payload": {
						        "repository_id": "42",
						        "branch": "main",
						        "source_uri": "https://git.example.internal/team/app.git",
						        "include_paths": ["src/**"],
						        "exclude_paths": ["src/generated/**"]
						      }
						    }
						  }
						}
						"""))
				.andRespond(withSuccess("{\"run_id\":\"run-2\"}", MediaType.APPLICATION_JSON));

		client.dispatchRepositorySync(
				7L,
				42L,
				RepositorySyncRequestType.REPOSITORY_ADDED,
				"repository-7-42-sync-1",
				"main",
				"https://git.example.internal/team/app.git",
				List.of("src/**"),
				List.of("src/generated/**")
		);

		server.verify();
	}

	@Test
	void dispatchesRepositoryRemovedWithoutSourceUri() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ClioAgentClient client = new ClioAgentClient(
				builder,
				new ClioAgentProperties(true, URI.create("http://agent:2024"))
		);
		server.expect(once(), requestTo("http://agent:2024/runs"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
				.andExpect(content().json("""
						{
						  "assistant_id": "clio_agent",
						  "input": {
						    "request": {
						      "request_id": "repository-7-42-remove-1",
						      "request_type": "repository_removed",
						      "project_id": "7",
						      "payload": {
						        "repository_id": "42",
						        "branch": "main"
						      }
						    }
						  }
						}
						"""))
				.andRespond(withSuccess("{\"run_id\":\"run-3\"}", MediaType.APPLICATION_JSON));

		client.dispatchRepositorySync(
				7L,
				42L,
				RepositorySyncRequestType.REPOSITORY_REMOVED,
				"repository-7-42-remove-1",
				"main",
				null,
				List.of(),
				List.of()
		);

		server.verify();
	}
}
