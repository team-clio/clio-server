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
class BugReviewLifecycleTest {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Autowired private MockMvc mockMvc;
	@Autowired private ProjectRepository projectRepository;

	@Test
	void listsBugsWaitingForMatchReviewWithTheSuggestedCandidate() throws Exception {
		Project project = projectRepository.save(Project.create("Review queue", null));
		long candidateIssueId = createIssueFromBug(project, "Existing failure");
		long bugId = reviewBug(project, candidateIssueId);

		mockMvc.perform(get(external(project) + "/bug-reviews"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].bug_id").value(bugId))
				.andExpect(jsonPath("$[0].candidate_issue_id").value(candidateIssueId))
				.andExpect(jsonPath("$[0].candidate_issue_title").value("Existing failure"))
				.andExpect(jsonPath("$[0].confidence").value(0.82))
				.andExpect(jsonPath("$[0].reason").value("증상은 같지만 오류 신호가 다릅니다."));
	}

	@Test
	void linksReviewedBugToTheChosenIssueAsManualGrouping() throws Exception {
		Project project = projectRepository.save(Project.create("Review link", null));
		long candidateIssueId = createIssueFromBug(project, "Existing failure");
		long bugId = reviewBug(project, candidateIssueId);

		mockMvc.perform(post(external(project) + "/bug-reviews/" + bugId + "/link")
						.contentType(MediaType.APPLICATION_JSON).content("{\"issue_id\":%d}".formatted(candidateIssueId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.issue_id").value(candidateIssueId));
		mockMvc.perform(post(external(project) + "/bug-reviews/" + bugId + "/link")
						.contentType(MediaType.APPLICATION_JSON).content("{\"issue_id\":%d}".formatted(candidateIssueId)))
				.andExpect(status().isConflict());

		mockMvc.perform(get(external(project) + "/bug-reviews"))
				.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(get(external(project) + "/issues/" + candidateIssueId))
				.andExpect(jsonPath("$.bugs[?(@.id == %d)].groupedBy".formatted(bugId)).value("MANUAL"));
	}

	@Test
	void createsNewIssueFromReviewByQueuingTheBugAgain() throws Exception {
		Project project = projectRepository.save(Project.create("Review create", null));
		long bugId = reviewBug(project, createIssueFromBug(project, "Existing failure"));

		mockMvc.perform(post(external(project) + "/bug-reviews/" + bugId + "/create-issue"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.status").value("NEW"));
		mockMvc.perform(post(external(project) + "/bug-reviews/" + bugId + "/create-issue"))
				.andExpect(status().isConflict());
	}

	@Test
	void rejectsCreatingIssueFromReviewWhileRepositoryIsNotSynced() throws Exception {
		Project project = projectRepository.save(Project.create("Review unsynced", null));
		long bugId = reviewBug(project, createIssueFromBug(project, "Existing failure"));
		mockMvc.perform(post("/api/v1/projects/{projectId}/repositories", project.getId())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"provider":"GITHUB","owner":"acme","name":"app","url":"https://github.com/acme/app","defaultBranch":"main","includePaths":[],"excludePaths":[],"enabled":true}
								"""))
				.andExpect(status().isCreated());

		mockMvc.perform(post(external(project) + "/bug-reviews/" + bugId + "/create-issue"))
				.andExpect(status().isConflict());
	}

	/** Agent가 기존 Issue 후보를 제안하며 검토를 요청한 버그를 만든다. */
	long reviewBug(Project project, long candidateIssueId) throws Exception {
		long bugId = collectBug(project, "Similar failure");
		long runId = startRun(project, bugId);
		mockMvc.perform(patch(internal(project) + "/workflow-runs/" + runId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status":"COMPLETED","result_snapshot":{"action":"needs_review","bug_id":"%d",
								"candidate_issue_id":"%d","confidence":0.82,"reason":"증상은 같지만 오류 신호가 다릅니다."}}
								""".formatted(bugId, candidateIssueId)))
				.andExpect(status().isOk());
		return bugId;
	}

	long createIssueFromBug(Project project, String title) throws Exception {
		long bugId = collectBug(project, title);
		long runId = startRun(project, bugId);
		return OBJECT_MAPPER.readTree(mockMvc.perform(post(internal(project) + "/issues")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"workflow_run_id":%d,"bug_id":%d,"confidence":1.0,"title":"%s","description":"desc"}
								""".formatted(runId, bugId, title)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("issue_id").asLong();
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

	long collectBug(Project project, String title) throws Exception {
		return OBJECT_MAPPER.readTree(mockMvc.perform(post(external(project) + "/bugs")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"%s\",\"source\":\"MANUAL\",\"occurred_at\":\"2026-10-03T00:00:00Z\"}".formatted(title)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
	}

	String external(Project project) {
		return "/external-api/v1/projects/" + project.getId();
	}

	String internal(Project project) {
		return "/internal-api/v1/projects/" + project.getId();
	}
}
