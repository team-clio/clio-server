package ax.clio.agent.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import ax.clio.common.PcmInspectUnavailableException;
import ax.clio.common.ResourceNotFoundException;

class PcmInspectClientTest {

	@Test
	void readsSnapshotWithStringProjectId() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		PcmInspectClient client = new PcmInspectClient(
				builder,
				new PcmInspectProperties(URI.create("http://agent-inspect:2025"))
		);
		server.expect(once(), requestTo("http://agent-inspect:2025/pcm/projects/3/snapshot"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("""
						{"project_id":"3","pcm_revision":1,"knowledge_index_revision":1,"repository_revisions":{}}
						""", MediaType.APPLICATION_JSON));

		Map<String, Object> snapshot = client.readSnapshot("3");

		assertEquals(1, snapshot.get("pcm_revision"));
		server.verify();
	}

	@Test
	void listsKnowledgeDocumentsForTheProject() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		PcmInspectClient client = new PcmInspectClient(
				builder,
				new PcmInspectProperties(URI.create("http://agent-inspect:2025"))
		);
		server.expect(once(), requestTo("http://agent-inspect:2025/pcm/projects/3/knowledge"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("""
						[{"project_id":"3","knowledge_id":"kn_1","logical_key":"saved-search-permissions",
						  "knowledge_type":"domain_rule","title":"Saved Search permissions",
						  "body_markdown":"Only the owner can edit.","knowledge_revision":1,
						  "valid_from_pcm_revision":1,"valid_until_pcm_revision":null,
						  "sources":[],"related_knowledge_ids":[],"is_tombstone":false}]
						""", MediaType.APPLICATION_JSON));

		List<Map<String, Object>> documents = client.listKnowledge("3");

		assertEquals(1, documents.size());
		assertEquals("kn_1", documents.get(0).get("knowledge_id"));
		server.verify();
	}

	@Test
	void readsKnowledgeDetailById() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		PcmInspectClient client = new PcmInspectClient(
				builder,
				new PcmInspectProperties(URI.create("http://agent-inspect:2025"))
		);
		server.expect(once(),
				requestTo("http://agent-inspect:2025/pcm/projects/3/knowledge/kn_1"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("""
						{"project_id":"3","knowledge_id":"kn_1","logical_key":"saved-search-permissions",
						  "knowledge_type":"domain_rule","title":"Saved Search permissions",
						  "body_markdown":"Only the owner can edit.","knowledge_revision":1,
						  "valid_from_pcm_revision":1,"valid_until_pcm_revision":null,
						  "sources":[{"source_type":"document","source_id":"requirements",
						    "source_revision":"1","locator":{},"content_hash":null}],
						  "related_knowledge_ids":[],"is_tombstone":false}
						""", MediaType.APPLICATION_JSON));

		Map<String, Object> document = client.readKnowledge("3", "kn_1");

		assertEquals("Only the owner can edit.", document.get("body_markdown"));
		server.verify();
	}

	@Test
	void mapsMissingKnowledgeToResourceNotFoundException() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		PcmInspectClient client = new PcmInspectClient(
				builder,
				new PcmInspectProperties(URI.create("http://agent-inspect:2025"))
		);
		server.expect(once(), requestTo("http://agent-inspect:2025/pcm/projects/3/knowledge/kn_x"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withStatus(HttpStatus.NOT_FOUND).body(
						"{\"detail\":\"Knowledge 'kn_x' is unavailable.\"}"
				));

		assertThrows(
				ResourceNotFoundException.class,
				() -> client.readKnowledge("3", "kn_x")
		);
		server.verify();
	}

	@Test
	void mapsUpstreamServerErrorToUnavailableException() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		PcmInspectClient client = new PcmInspectClient(
				builder,
				new PcmInspectProperties(URI.create("http://agent-inspect:2025"))
		);
		server.expect(once(), requestTo("http://agent-inspect:2025/pcm/projects/3/knowledge"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body(
						"{\"detail\":\"PCM inspect unavailable.\"}"
				));

		assertThrows(
				PcmInspectUnavailableException.class,
				() -> client.listKnowledge("3")
		);
		server.verify();
	}
}
