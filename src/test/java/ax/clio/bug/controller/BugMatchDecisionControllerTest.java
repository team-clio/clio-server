package ax.clio.bug.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;

import ax.clio.bug.entity.Bug;
import ax.clio.bug.entity.BugOccurrence;
import ax.clio.bug.entity.BugSource;
import ax.clio.bug.repository.BugMatchDecisionRepository;
import ax.clio.bug.repository.BugOccurrenceRepository;
import ax.clio.bug.repository.BugRepository;
import ax.clio.issue.entity.Issue;
import ax.clio.issue.repository.IssueBugRepository;
import ax.clio.issue.repository.IssueRepository;
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
class BugMatchDecisionControllerTest {

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
	private BugMatchDecisionRepository decisionRepository;

	@Test
	void acceptsSnakeCaseAndReplaysSameRequest() throws Exception {
		Project project = projectRepository.save(Project.create("Clio", null));
		Instant occurredAt = Instant.parse("2026-08-10T00:00:00Z");
		BugOccurrence report = BugOccurrence.collect(
				project,
				BugSource.API,
				"Saved search fails",
				"HTTP 500",
				"IllegalStateException",
				"saved search failed",
				java.util.List.of("SavedSearchService.run"),
				OBJECT_MAPPER.createObjectNode(),
				occurredAt
		);
		Bug bug = bugRepository.save(Bug.createFrom(report));
		report = occurrenceRepository.save(report);
		Issue issue = issueRepository.save(Issue.createFromBug(bug, BigDecimal.ONE));
		String body = requestBody("REQ-CONTROLLER", bug.getId(), report.getId(), issue.getId());
		String endpoint = "/internal/api/v1/projects/%d/bugs/%d/match-decisions"
				.formatted(project.getId(), bug.getId());

		mockMvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(header().string("Idempotent-Replayed", "false"))
				.andExpect(jsonPath("$.bug_id").value(bug.getId()))
				.andExpect(jsonPath("$.resulting_issue_id").value(issue.getId()))
				.andExpect(jsonPath("$.linked").value(true));
		mockMvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(header().string("Idempotent-Replayed", "true"));

		assertThat(issueBugRepository.count()).isEqualTo(1);
		assertThat(decisionRepository.count()).isEqualTo(1);
	}

	private String requestBody(String requestId, Long bugId, Long reportId, Long issueId) {
		return """
				{
				  "request_id": "%s",
				  "bug_report_id": %d,
				  "match_decision": {
				    "bug_id": %d,
				    "action": "AUTO_LINK",
				    "matched_issue_id": %d,
				    "confidence": 0.97,
				    "supporting_reasons": ["same error"],
				    "contradictions": [],
				    "review_reasons": [],
				    "candidate_comparisons": []
				  }
				}
				""".formatted(requestId, reportId, bugId, issueId);
	}
}
