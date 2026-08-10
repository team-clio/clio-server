package ax.clio.analysis.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;

import ax.clio.analysis.repository.AnalysisJobRepository;
import ax.clio.analysis.repository.AnalysisResultRepository;
import ax.clio.bug.entity.Bug;
import ax.clio.bug.entity.BugOccurrence;
import ax.clio.bug.entity.BugSource;
import ax.clio.bug.repository.BugOccurrenceRepository;
import ax.clio.bug.repository.BugRepository;
import ax.clio.issue.entity.Issue;
import ax.clio.issue.entity.IssueBug;
import ax.clio.issue.repository.IssueBugRepository;
import ax.clio.issue.repository.IssueRepository;
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
class AnalysisJobControllerTest {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private BugRepository bugRepository;

	@Autowired
	private BugOccurrenceRepository occurrenceRepository;

	@Autowired
	private IssueRepository issueRepository;

	@Autowired
	private IssueBugRepository issueBugRepository;

	@Autowired
	private AnalysisJobRepository jobRepository;

	@Autowired
	private AnalysisResultRepository resultRepository;

	@Test
	void supportsSnakeCaseLifecycleAndIdempotentCreateReplay() throws Exception {
		Fixture fixture = fixture();
		String createEndpoint = "/api/v1/projects/%d/issues/%d/analysis-jobs"
				.formatted(fixture.project().getId(), fixture.issue().getId());
		String createBody = """
				{
				  "request_id": "REQ-HTTP-CREATE",
				  "trigger_bug_id": %d
				}
				""".formatted(fixture.bug().getId());

		String responseBody = mockMvc.perform(post(createEndpoint)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody))
				.andExpect(status().isCreated())
				.andExpect(header().string("Idempotent-Replayed", "false"))
				.andExpect(jsonPath("$.analysis_job_id").isNumber())
				.andExpect(jsonPath("$.trigger_bug_id").value(fixture.bug().getId()))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andReturn().getResponse().getContentAsString();
		long jobId = OBJECT_MAPPER.readTree(responseBody).get("analysis_job_id").asLong();

		mockMvc.perform(post(createEndpoint)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody))
				.andExpect(status().isCreated())
				.andExpect(header().string("Idempotent-Replayed", "true"))
				.andExpect(jsonPath("$.analysis_job_id").value(jobId));
		assertThat(jobRepository.count()).isEqualTo(1);

		String jobEndpoint = "/api/v1/projects/%d/analysis-jobs/%d"
				.formatted(fixture.project().getId(), jobId);
		mockMvc.perform(get(jobEndpoint + "/context"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.issue.issue_id").value(fixture.issue().getId()))
				.andExpect(jsonPath("$.bugs[0].latest_bug_report_id")
						.value(fixture.report().getId()))
				.andExpect(jsonPath("$.previous_analysis").doesNotExist());

		mockMvc.perform(patch(jobEndpoint)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "request_id": "REQ-HTTP-START",
								  "status": "RUNNING"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("RUNNING"));

		String resultBody = """
				{
				  "request_id": "REQ-HTTP-RESULT",
				  "issue_analysis": {
				    "analysis_job_id": %d,
				    "project_id": %d,
				    "issue_id": %d,
				    "status": "INSUFFICIENT_EVIDENCE",
				    "evidence": [],
				    "relations": [],
				    "findings": [],
				    "hypotheses": [],
				    "warnings": ["no repository context"]
				  }
				}
				""".formatted(jobId, fixture.project().getId(), fixture.issue().getId());
		mockMvc.perform(put(jobEndpoint + "/result")
						.contentType(MediaType.APPLICATION_JSON)
						.content(resultBody))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.analysis_job_id").value(jobId))
				.andExpect(jsonPath("$.job_status").value("COMPLETED"))
				.andExpect(jsonPath("$.result_status").value("INSUFFICIENT_EVIDENCE"));

		assertThat(resultRepository.findByJobId(jobId)).isPresent();
	}

	@Test
	void rejectsCamelCaseAgentRequest() throws Exception {
		Fixture fixture = fixture();
		String endpoint = "/api/v1/projects/%d/issues/%d/analysis-jobs"
				.formatted(fixture.project().getId(), fixture.issue().getId());

		mockMvc.perform(post(endpoint)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "requestId": "REQ-CAMEL",
								  "triggerBugId": %d
								}
								""".formatted(fixture.bug().getId())))
				.andExpect(status().isBadRequest());
	}

	private Fixture fixture() {
		Project project = projectRepository.save(Project.create("Clio", null));
		Instant occurredAt = Instant.parse("2026-08-10T00:00:00Z");
		Bug bug = bugRepository.save(Bug.create(
				project,
				"analysis-controller-fingerprint",
				"source",
				"Saved search fails",
				"HTTP 500",
				BugSource.API,
				"IllegalStateException",
				"saved search failed",
				"SavedSearchService.run",
				occurredAt
		));
		JsonNode rawPayload = OBJECT_MAPPER.createObjectNode().put("status", 500);
		BugOccurrence report = occurrenceRepository.save(BugOccurrence.create(
				bug,
				BugSource.API,
				rawPayload,
				occurredAt
		));
		Issue issue = issueRepository.save(Issue.createFromBug(bug, BigDecimal.ONE));
		issue.attach(bug, BigDecimal.ONE);
		issueBugRepository.save(IssueBug.create(issue, bug, BigDecimal.ONE));
		return new Fixture(project, bug, report, issue);
	}

	private record Fixture(Project project, Bug bug, BugOccurrence report, Issue issue) {
	}
}
