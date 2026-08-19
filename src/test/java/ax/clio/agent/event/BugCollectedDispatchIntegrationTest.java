package ax.clio.agent.event;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ax.clio.agent.client.ClioAgentClient;
import ax.clio.bug.repository.BugRepository;
import ax.clio.project.entity.Project;
import ax.clio.project.repository.ProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "clio.agent.enabled=true")
@AutoConfigureMockMvc
class BugCollectedDispatchIntegrationTest {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Autowired private MockMvc mockMvc;
	@Autowired private ProjectRepository projectRepository;
	@Autowired private BugRepository bugRepository;
	@MockitoBean private ClioAgentClient agentClient;

	@AfterEach
	void cleanUp() {
		bugRepository.deleteAll();
		projectRepository.deleteAll();
	}

	@Test
	void keepsBugWaitingWhenTheProjectHasNoRepository() throws Exception {
		Project project = projectRepository.save(Project.create("Agent dispatch", null));

		String response = mockMvc.perform(post("/external-api/v1/projects/" + project.getId() + "/bugs")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "title": "Saved search fails",
								  "source": "API",
								  "message": "saved search failed",
								  "stack_trace": [],
								  "occurred_at": "2026-08-13T00:00:00Z"
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		JsonNode collected = OBJECT_MAPPER.readTree(response);

		verifyNoInteractions(agentClient);
		org.assertj.core.api.Assertions.assertThat(
				bugRepository.findById(collected.get("id").asLong()).orElseThrow().getStatus()
		).isEqualTo(ax.clio.bug.entity.BugStatus.NEW);
	}
}
