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

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProjectRepository projectRepository;

	@Test
	void completesReportToIssueAnalysisLifecycle() throws Exception {
		Project project = projectRepository.save(Project.create("Clio", null));
		String projectPath = "/api/v1/projects/" + project.getId();

		JsonNode collected = json(mockMvc.perform(post(projectPath + "/bug-reports")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "title": "Saved search fails",
								  "description": "HTTP 500 after clicking save",
								  "source": "API",
								  "errorType": "IllegalStateException",
								  "message": "saved search failed",
								  "stackTrace": ["SavedSearchService.run"],
								  "occurredAt": "2026-08-10T00:00:00Z"
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
		long reportId = collected.get("id").asLong();

		mockMvc.perform(get(projectPath + "/bug-reports/" + reportId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bug_report_id").value(reportId));

		JsonNode grouped = json(mockMvc.perform(post(
						projectPath + "/bug-reports/" + reportId + "/grouping-decisions"
				)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "request_id": "REQ-LIFECYCLE-GROUP",
								  "grouping_decision": {
								    "bug_report_id": %d,
								    "action": "CREATE_NEW",
								    "confidence": 0.0
								  }
								}
								""".formatted(reportId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
		long bugId = grouped.get("resulting_bug_id").asLong();

		JsonNode matched = json(mockMvc.perform(post(
						projectPath + "/bugs/" + bugId + "/match-decisions"
				)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "request_id": "REQ-LIFECYCLE-MATCH",
								  "bug_report_id": %d,
								  "match_decision": {
								    "bug_id": %d,
								    "action": "CREATE_NEW",
								    "confidence": 0.0
								  }
								}
								""".formatted(reportId, bugId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
		long issueId = matched.get("resulting_issue_id").asLong();

		JsonNode createdJob = json(mockMvc.perform(post(
						projectPath + "/issues/" + issueId + "/analysis-jobs"
				)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "request_id": "REQ-LIFECYCLE-JOB",
								  "trigger_bug_id": %d
								}
								""".formatted(bugId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
		long jobId = createdJob.get("analysis_job_id").asLong();
		String jobPath = projectPath + "/analysis-jobs/" + jobId;

		mockMvc.perform(get(jobPath + "/context"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.issue.issue_id").value(issueId))
				.andExpect(jsonPath("$.trigger_bug_id").value(bugId));

		mockMvc.perform(patch(jobPath)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "request_id": "REQ-LIFECYCLE-START",
								  "status": "RUNNING"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("RUNNING"));

		mockMvc.perform(put(jobPath + "/result")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "request_id": "REQ-LIFECYCLE-RESULT",
								  "issue_analysis": {
								    "analysis_job_id": %d,
								    "project_id": %d,
								    "issue_id": %d,
								    "status": "INSUFFICIENT_EVIDENCE",
								    "evidence": [],
								    "relations": [],
								    "findings": [],
								    "hypotheses": [],
								    "warnings": ["repository is not synchronized"]
								  }
								}
								""".formatted(jobId, project.getId(), issueId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.job_status").value("COMPLETED"));

		mockMvc.perform(get(projectPath + "/issues/" + issueId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(issueId))
				.andExpect(jsonPath("$.reportCount").value(1));
	}

	private JsonNode json(String value) throws Exception {
		return OBJECT_MAPPER.readTree(value);
	}
}
