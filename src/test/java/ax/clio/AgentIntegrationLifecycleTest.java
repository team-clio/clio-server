package ax.clio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ax.clio.project.entity.Project;
import ax.clio.project.repository.ProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
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
class AgentIntegrationLifecycleTest {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Autowired private MockMvc mockMvc;
	@Autowired private ProjectRepository projectRepository;

	@Test
	void completesBugToIssueAnalysisWorkflow() throws Exception {
		Project project = projectRepository.save(Project.create("Clio", null));
		String external = "/external-api/v1/projects/" + project.getId();
		String internal = "/internal-api/v1/projects/" + project.getId();

		JsonNode collected = json(mockMvc.perform(post(external + "/bugs")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "title": "Saved search fails",
								  "description": "HTTP 500 after clicking save",
								  "source": "API",
								  "error_type": "IllegalStateException",
								  "message": "saved search failed",
								  "stack_trace": ["SavedSearchService.run"],
								  "occurred_at": "2026-08-10T00:00:00Z"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("NEW"))
				.andReturn().getResponse().getContentAsString());
		long bugId = collected.get("id").asLong();

		mockMvc.perform(get(internal + "/bugs/" + bugId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bug_id").value(bugId))
				.andExpect(jsonPath("$.stack_trace[0]").value("SavedSearchService.run"));

		JsonNode workflow = json(mockMvc.perform(post(internal + "/workflow-runs")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "request_id": "REQ-LIFECYCLE",
								  "request_type": "process_report",
								  "request_payload": {"project_id": %d, "bug_id": %d}
								}
								""".formatted(project.getId(), bugId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andReturn().getResponse().getContentAsString());
		long runId = workflow.get("id").asLong();

		mockMvc.perform(patch(internal + "/workflow-runs/" + runId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"RUNNING\",\"latest_checkpoint\":{\"node\":\"normalize\"}}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("RUNNING"));

		JsonNode createdIssue = json(mockMvc.perform(post(internal + "/issues")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "workflow_run_id": %d,
								  "bug_id": %d,
								  "confidence": 0.0,
								  "title": "저장된 검색이 실패합니다",
								  "description": "## 증상\\n저장 요청이 실패합니다."
								}
								""".formatted(runId, bugId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.issue_created").value(true))
				.andExpect(jsonPath("$.bug_linked").value(true))
				.andReturn().getResponse().getContentAsString());
		long issueId = createdIssue.get("issue_id").asLong();
		mockMvc.perform(get(external + "/issues/" + issueId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("저장된 검색이 실패합니다"))
				.andExpect(jsonPath("$.summary").value("## 증상\n저장 요청이 실패합니다."));

		mockMvc.perform(post(internal + "/issues")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "workflow_run_id": %d,
								  "bug_id": %d,
								  "confidence": 0.0
								}
								""".formatted(runId, bugId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.issue_id").value(issueId))
				.andExpect(jsonPath("$.issue_created").value(false))
				.andExpect(jsonPath("$.bug_linked").value(false));

		JsonNode secondBug = json(mockMvc.perform(post(external + "/bugs")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "title": "Saved search fails again",
								  "source": "API",
								  "message": "same failure",
								  "stack_trace": [],
								  "occurred_at": "2026-08-11T00:00:00Z"
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
		long secondBugId = secondBug.get("id").asLong();
		JsonNode linkWorkflow = json(mockMvc.perform(post(internal + "/workflow-runs")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "request_id": "REQ-LINK-EXISTING",
								  "request_type": "process_report",
								  "request_payload": {"bug_id": %d}
								}
								""".formatted(secondBugId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
		long linkRunId = linkWorkflow.get("id").asLong();
		mockMvc.perform(patch(internal + "/workflow-runs/" + linkRunId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"RUNNING\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(post(internal + "/issues/" + issueId + "/bugs")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "workflow_run_id": %d,
								  "bug_id": %d,
								  "confidence": 0.97
								}
								""".formatted(linkRunId, secondBugId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.issue_id").value(issueId))
				.andExpect(jsonPath("$.issue_created").value(false))
				.andExpect(jsonPath("$.bug_linked").value(true));
		mockMvc.perform(patch(internal + "/workflow-runs/" + linkRunId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"COMPLETED\",\"result_snapshot\":{\"action\":\"link_existing\"}}"))
				.andExpect(status().isOk());
		mockMvc.perform(get(internal + "/issues/" + issueId + "/bugs/representative"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bug_id").value(secondBugId))
				.andExpect(jsonPath("$.message").value("same failure"));

		mockMvc.perform(put(internal + "/workflow-runs/" + runId + "/analysis-result")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "issue_id": %d,
								  "issue_analysis": {
								    "workflow_run_id": %d,
								    "project_id": %d,
								    "issue_id": %d,
								    "status": "INSUFFICIENT_EVIDENCE",
								    "evidence": [], "relations": [], "findings": [], "hypotheses": []
								  }
								}
								""".formatted(issueId, runId, project.getId(), issueId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.workflow_run_id").value(runId));

		mockMvc.perform(patch(internal + "/workflow-runs/" + runId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"COMPLETED\",\"result_snapshot\":{\"issue_id\":" + issueId + "}}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"));

		mockMvc.perform(get(external + "/issues/" + issueId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bugCount").value(2))
				.andExpect(jsonPath("$.bugs[0].id").value(secondBugId))
				.andExpect(jsonPath("$.bugs[1].id").value(bugId));

		mockMvc.perform(get(external + "/issues/stats"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalIssues").value(1))
				.andExpect(jsonPath("$.totalBugs").value(2));

		mockMvc.perform(get(internal + "/issues/" + issueId + "/analysis-results/latest"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.workflow_run_id").value(runId))
				.andExpect(jsonPath("$.issue_analysis.status").value("INSUFFICIENT_EVIDENCE"));
		mockMvc.perform(get(external + "/issues/" + issueId + "/analysis-results/latest"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.workflowRunId").value(runId))
				.andExpect(jsonPath("$.issueAnalysis.status").value("INSUFFICIENT_EVIDENCE"));
	}

	@Test
	void workflowRequestIdIsIdempotentAndRejectsDifferentPayload() throws Exception {
		Project project = projectRepository.save(Project.create("Clio", null));
		String path = "/internal-api/v1/projects/" + project.getId() + "/workflow-runs";
		String body = """
				{"request_id":"REQ-SAME","request_type":"document_added","request_payload":{"document_id":"D1"}}
				""";

		JsonNode first = json(mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(first.get("id").asLong()));
		String runPath = path + "/" + first.get("id").asLong();
		String running = "{\"status\":\"RUNNING\"}";
		mockMvc.perform(patch(runPath).contentType(MediaType.APPLICATION_JSON).content(running))
				.andExpect(status().isOk());
		mockMvc.perform(patch(runPath).contentType(MediaType.APPLICATION_JSON).content(running))
				.andExpect(status().isConflict());
		mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(
						"{\"request_id\":\"REQ-SAME\",\"request_type\":\"document_added\",\"request_payload\":{\"document_id\":\"D2\"}}"))
				.andExpect(status().isConflict());
	}

	private JsonNode json(String value) throws Exception {
		return OBJECT_MAPPER.readTree(value);
	}
}
