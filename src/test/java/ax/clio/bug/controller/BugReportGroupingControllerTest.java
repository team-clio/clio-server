package ax.clio.bug.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ax.clio.bug.repository.BugGroupingDecisionRepository;
import ax.clio.bug.repository.BugOccurrenceRepository;
import ax.clio.bug.repository.BugRepository;
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
class BugReportGroupingControllerTest {

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
	private BugGroupingDecisionRepository decisionRepository;

	@Test
	void collectsRawReportThenAppliesAgentCreateDecisionIdempotently() throws Exception {
		Project project = projectRepository.save(Project.create("Clio", null));
		String reportsEndpoint = "/api/v1/projects/%d/bug-reports".formatted(project.getId());
		String collectBody = """
				{
				  "title": "Saved search fails",
				  "description": "HTTP 500",
				  "source": "API",
				  "errorType": "IllegalStateException",
				  "message": "saved search failed",
				  "stackTrace": ["SavedSearchService.run"],
				  "occurredAt": "2026-08-10T00:00:00Z",
				  "rawPayload": {"status": 500}
				}
				""";

		String collected = mockMvc.perform(post(reportsEndpoint)
						.contentType(MediaType.APPLICATION_JSON)
						.content(collectBody))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.bugId").doesNotExist())
				.andExpect(jsonPath("$.status").value("PENDING_GROUPING"))
				.andReturn().getResponse().getContentAsString();
		long reportId = OBJECT_MAPPER.readTree(collected).get("id").asLong();

		mockMvc.perform(get(reportsEndpoint + "/" + reportId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bug_report_id").value(reportId))
				.andExpect(jsonPath("$.error_type").value("IllegalStateException"))
				.andExpect(jsonPath("$.stack_trace[0]").value("SavedSearchService.run"))
				.andExpect(jsonPath("$.raw_payload.status").value(500));

		String groupingEndpoint = reportsEndpoint + "/" + reportId + "/grouping-decisions";
		String groupingBody = """
				{
				  "request_id": "REQ-GROUP-HTTP",
				  "grouping_decision": {
				    "bug_report_id": %d,
				    "action": "CREATE_NEW",
				    "confidence": 0.98,
				    "supporting_reasons": ["no same Bug candidate"],
				    "contradictions": [],
				    "review_reasons": [],
				    "candidate_comparisons": []
				  }
				}
				""".formatted(reportId);
		mockMvc.perform(post(groupingEndpoint)
						.contentType(MediaType.APPLICATION_JSON)
						.content(groupingBody))
				.andExpect(status().isCreated())
				.andExpect(header().string("Idempotent-Replayed", "false"))
				.andExpect(jsonPath("$.bug_report_id").value(reportId))
				.andExpect(jsonPath("$.resulting_bug_id").isNumber())
				.andExpect(jsonPath("$.ready_for_issue_matching").value(true));
		mockMvc.perform(post(groupingEndpoint)
						.contentType(MediaType.APPLICATION_JSON)
						.content(groupingBody))
				.andExpect(status().isCreated())
				.andExpect(header().string("Idempotent-Replayed", "true"));

		assertThat(bugRepository.count()).isEqualTo(1);
		assertThat(decisionRepository.count()).isEqualTo(1);
		assertThat(occurrenceRepository.findById(reportId).orElseThrow().getBug()).isNotNull();
	}

	@Test
	void listsUngroupedReports() throws Exception {
		Project project = projectRepository.save(Project.create("Clio", null));
		String endpoint = "/api/v1/projects/%d/bug-reports".formatted(project.getId());
		mockMvc.perform(post(endpoint)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "title": "Failure",
								  "source": "API",
								  "occurredAt": "2026-08-10T00:00:00Z"
								}
								"""))
				.andExpect(status().isCreated());

		mockMvc.perform(get(endpoint))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].bugId").doesNotExist())
				.andExpect(jsonPath("$.items[0].status").value("PENDING_GROUPING"))
				.andExpect(jsonPath("$.totalElements").value(1));
	}
}
