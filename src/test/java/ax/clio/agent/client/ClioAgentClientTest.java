package ax.clio.agent.client;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ClioAgentClientTest {

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
}
