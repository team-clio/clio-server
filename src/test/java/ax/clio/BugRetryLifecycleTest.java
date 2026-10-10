package ax.clio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ax.clio.project.entity.Project;
import ax.clio.project.repository.ProjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BugRetryLifecycleTest {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Autowired private MockMvc mockMvc;
	@Autowired private ProjectRepository projectRepository;

	@Test
	void retriesFailedBugByReturningItToNew() throws Exception {
		Project project = projectRepository.save(Project.create("Retry", null));
		long bugId = failedBug(project);

		mockMvc.perform(post(external(project) + "/bugs/" + bugId + "/retry"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.status").value("NEW"));
		mockMvc.perform(get(external(project) + "/bugs"))
				.andExpect(jsonPath("$.items[0].status").value("NEW"));
	}

	@Test
	void rejectsRetryOfBugThatHasNotFailed() throws Exception {
		Project project = projectRepository.save(Project.create("Not failed", null));
		long bugId = collectBug(project);

		mockMvc.perform(post(external(project) + "/bugs/" + bugId + "/retry"))
				.andExpect(status().isConflict());
	}

	private long failedBug(Project project) throws Exception {
		long bugId = collectBug(project);
		mockMvc.perform(patch(external(project) + "/bugs/" + bugId)
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ANALYZING\"}"))
				.andExpect(status().isOk());
		long runId = OBJECT_MAPPER.readTree(mockMvc.perform(post(internal(project) + "/workflow-runs")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"request_id\":\"process-bug-%d\",\"request_type\":\"process_report\",\"request_payload\":{\"bug_id\":%d}}"
								.formatted(bugId, bugId)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
		String runPath = internal(project) + "/workflow-runs/" + runId;
		mockMvc.perform(patch(runPath).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RUNNING\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(patch(runPath).contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"FAILED\",\"failure_code\":\"Error\",\"failure_message\":\"failed\"}"))
				.andExpect(status().isOk());
		return bugId;
	}

	private long collectBug(Project project) throws Exception {
		return OBJECT_MAPPER.readTree(mockMvc.perform(post(external(project) + "/bugs")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Bug\",\"source\":\"MANUAL\",\"occurred_at\":\"2026-10-03T00:00:00Z\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
	}

	private String external(Project project) {
		return "/external-api/v1/projects/" + project.getId();
	}

	private String internal(Project project) {
		return "/internal-api/v1/projects/" + project.getId();
	}
}
