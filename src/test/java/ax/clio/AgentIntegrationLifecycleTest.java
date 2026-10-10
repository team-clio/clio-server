package ax.clio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ax.clio.project.entity.Project;
import ax.clio.project.repository.ProjectRepository;
import ax.clio.workflow.service.AgentWorkflowRunService;
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
	@Autowired private AgentWorkflowRunService workflowRunService;

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

	@Test
	void completesReviewWithoutLeavingBugAnalyzing() throws Exception {
		Project project = projectRepository.save(Project.create("Review", null));
		String external = "/external-api/v1/projects/" + project.getId();
		String internal = "/internal-api/v1/projects/" + project.getId();
		long bugId = json(mockMvc.perform(post(external + "/bugs")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Review bug\",\"source\":\"MANUAL\",\"occurred_at\":\"2026-10-03T00:00:00Z\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
		mockMvc.perform(patch(external + "/bugs/" + bugId)
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ANALYZING\"}"))
				.andExpect(status().isOk());
		long runId = json(mockMvc.perform(post(internal + "/workflow-runs")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"request_id\":\"REVIEW\",\"request_type\":\"process_report\",\"request_payload\":{\"bug_id\":%d}}".formatted(bugId)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
		String runPath = internal + "/workflow-runs/" + runId;
		mockMvc.perform(patch(runPath).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RUNNING\"}"))
				.andExpect(status().isOk());
		String completed = "{\"status\":\"COMPLETED\",\"result_snapshot\":{\"action\":\"needs_review\",\"bug_id\":\"%d\"}}".formatted(bugId);
		mockMvc.perform(patch(runPath).contentType(MediaType.APPLICATION_JSON).content(completed))
				.andExpect(status().isOk());
		mockMvc.perform(patch(runPath).contentType(MediaType.APPLICATION_JSON).content(completed))
				.andExpect(status().isOk());
		mockMvc.perform(get(external + "/bugs"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items[0].status").value("TRIAGED"))
				.andExpect(jsonPath("$.items[0].issue_id").isEmpty());
	}

	@Test
	void failedProcessingMarksAnalyzingBugFailed() throws Exception {
		Project project = projectRepository.save(Project.create("Failure", null));
		String external = "/external-api/v1/projects/" + project.getId();
		String internal = "/internal-api/v1/projects/" + project.getId();
		long bugId = json(mockMvc.perform(post(external + "/bugs")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Failing bug\",\"source\":\"MANUAL\",\"occurred_at\":\"2026-10-03T00:00:00Z\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
		mockMvc.perform(patch(external + "/bugs/" + bugId)
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ANALYZING\"}"))
				.andExpect(status().isOk());
		long runId = json(mockMvc.perform(post(internal + "/workflow-runs")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"request_id\":\"process-bug-%d\",\"request_type\":\"process_report\",\"request_payload\":{\"bug_id\":%d}}".formatted(bugId, bugId)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
		String runPath = internal + "/workflow-runs/" + runId;
		mockMvc.perform(patch(runPath).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RUNNING\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(patch(runPath).contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"FAILED\",\"failure_code\":\"RetrievalOperationError\",\"failure_message\":\"embed failed\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(get(external + "/bugs"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items[0].status").value("FAILED"));
	}

	@Test
	void failedProcessingKeepsBugStatusChangedByUser() throws Exception {
		Project project = projectRepository.save(Project.create("Ignored", null));
		String external = "/external-api/v1/projects/" + project.getId();
		String internal = "/internal-api/v1/projects/" + project.getId();
		long bugId = json(mockMvc.perform(post(external + "/bugs")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Ignored bug\",\"source\":\"MANUAL\",\"occurred_at\":\"2026-10-03T00:00:00Z\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
		long runId = json(mockMvc.perform(post(internal + "/workflow-runs")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"request_id\":\"process-bug-%d\",\"request_type\":\"process_report\",\"request_payload\":{\"bug_id\":%d}}".formatted(bugId, bugId)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
		mockMvc.perform(patch(external + "/bugs/" + bugId)
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IGNORED\"}"))
				.andExpect(status().isOk());
		String runPath = internal + "/workflow-runs/" + runId;
		mockMvc.perform(patch(runPath).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RUNNING\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(patch(runPath).contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"FAILED\",\"failure_code\":\"Error\",\"failure_message\":\"failed\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(get(external + "/bugs"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items[0].status").value("IGNORED"));
	}

	@Test
	void retryRequestIdPreservesPreviousAttempts() throws Exception {
		Project project = projectRepository.save(Project.create("Retry ids", null));
		String internal = "/internal-api/v1/projects/" + project.getId();
		assertThat(workflowRunService.nextProcessReportRequestId(project.getId(), 7L)).isEqualTo("process-bug-7");

		for (String requestId : new String[] {"process-bug-7", "process-bug-70"}) {
			mockMvc.perform(post(internal + "/workflow-runs")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"request_id\":\"%s\",\"request_type\":\"process_report\",\"request_payload\":{\"bug_id\":7}}".formatted(requestId)))
					.andExpect(status().isCreated());
		}
		assertThat(workflowRunService.nextProcessReportRequestId(project.getId(), 7L)).isEqualTo("process-bug-7-retry-1");

		mockMvc.perform(post(internal + "/workflow-runs")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"request_id\":\"process-bug-7-retry-1\",\"request_type\":\"process_report\",\"request_payload\":{\"bug_id\":7}}"))
				.andExpect(status().isCreated());
		assertThat(workflowRunService.nextProcessReportRequestId(project.getId(), 7L)).isEqualTo("process-bug-7-retry-2");
	}

	private JsonNode json(String value) throws Exception {
		return OBJECT_MAPPER.readTree(value);
	}
}
