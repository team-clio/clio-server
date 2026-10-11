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

/**
 * Agent가 Issue에 연결한 뒤에도 분석이 끝나기 전까지 버그는 처리 중이어야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BugTriageTimingTest {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Autowired private MockMvc mockMvc;
	@Autowired private ProjectRepository projectRepository;

	@Test
	void bugStaysAnalyzingAfterIssueCreationUntilTheWorkflowCompletes() throws Exception {
		Project project = projectRepository.save(Project.create("Triage timing", null));
		long bugId = collectBug(project);
		long runId = startRun(project, bugId);

		createIssue(project, runId, bugId);
		assertBugStatus(project, "ANALYZING");

		completeRun(project, runId, "{\"action\":\"analysis_completed\",\"bug_id\":\"%d\"}".formatted(bugId));
		assertBugStatus(project, "TRIAGED");
	}

	@Test
	void bugBecomesFailedWhenAnalysisFailsAfterIssueCreation() throws Exception {
		Project project = projectRepository.save(Project.create("Triage failure", null));
		long bugId = collectBug(project);
		long runId = startRun(project, bugId);

		createIssue(project, runId, bugId);
		mockMvc.perform(patch(internal(project) + "/workflow-runs/" + runId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"FAILED\",\"failure_code\":\"Error\",\"failure_message\":\"analysis failed\"}"))
				.andExpect(status().isOk());

		assertBugStatus(project, "FAILED");
	}

	void assertBugStatus(Project project, String expected) throws Exception {
		mockMvc.perform(get(external(project) + "/bugs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].status").value(expected));
	}

	void createIssue(Project project, long runId, long bugId) throws Exception {
		mockMvc.perform(post(internal(project) + "/issues")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"workflow_run_id\":%d,\"bug_id\":%d,\"confidence\":1.0,\"title\":\"Issue\",\"description\":\"desc\"}"
								.formatted(runId, bugId)))
				.andExpect(status().isCreated());
	}

	void completeRun(Project project, long runId, String result) throws Exception {
		mockMvc.perform(patch(internal(project) + "/workflow-runs/" + runId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"COMPLETED\",\"result_snapshot\":%s}".formatted(result)))
				.andExpect(status().isOk());
	}

	long startRun(Project project, long bugId) throws Exception {
		mockMvc.perform(patch(external(project) + "/bugs/" + bugId)
						.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ANALYZING\"}"))
				.andExpect(status().isOk());
		long runId = OBJECT_MAPPER.readTree(mockMvc.perform(post(internal(project) + "/workflow-runs")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"request_id\":\"process-bug-%d\",\"request_type\":\"process_report\",\"request_payload\":{\"bug_id\":%d}}"
								.formatted(bugId, bugId)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
		mockMvc.perform(patch(internal(project) + "/workflow-runs/" + runId)
						.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RUNNING\"}"))
				.andExpect(status().isOk());
		return runId;
	}

	long collectBug(Project project) throws Exception {
		return OBJECT_MAPPER.readTree(mockMvc.perform(post(external(project) + "/bugs")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Bug\",\"source\":\"MANUAL\",\"occurred_at\":\"2026-10-03T00:00:00Z\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
	}

	String external(Project project) {
		return "/external-api/v1/projects/" + project.getId();
	}

	String internal(Project project) {
		return "/internal-api/v1/projects/" + project.getId();
	}
}
