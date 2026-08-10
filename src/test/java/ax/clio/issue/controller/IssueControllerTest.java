package ax.clio.issue.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IssueControllerTest {

	private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
			new com.fasterxml.jackson.databind.ObjectMapper();

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

	@Test
	void returnsIssueListDetailAndStatsFromLinkedReports() throws Exception {
		Fixture fixture = fixture();
		String endpoint = "/api/v1/projects/%d/issues".formatted(fixture.project().getId());

		mockMvc.perform(get(endpoint).param("status", "OPEN"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.items[0].id").value(fixture.issue().getId()))
				.andExpect(jsonPath("$.items[0].reportCount").value(2));

		mockMvc.perform(get(endpoint + "/" + fixture.issue().getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reportCount").value(2))
				.andExpect(jsonPath("$.reports.length()").value(2))
				.andExpect(jsonPath("$.reports[0].title").value("Second failure"));

		mockMvc.perform(get(endpoint + "/stats"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalIssues").value(1))
				.andExpect(jsonPath("$.openIssues").value(1))
				.andExpect(jsonPath("$.totalReports").value(2))
				.andExpect(jsonPath("$.dailyReports[0].count").value(1))
				.andExpect(jsonPath("$.dailyReports[1].count").value(1));
	}

	@Test
	void rejectsUnsupportedSort() throws Exception {
		Project project = projectRepository.save(Project.create("Clio", null));
		String endpoint = "/api/v1/projects/%d/issues".formatted(project.getId());

		mockMvc.perform(get(endpoint).param("sort", "title,sideways"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	private Fixture fixture() {
		Project project = projectRepository.save(Project.create("Clio", null));
		BugOccurrence first = report(
				project,
				"First failure",
				Instant.parse("2026-08-09T23:50:00Z")
		);
		Bug bug = bugRepository.save(Bug.createFrom(first));
		Issue issue = issueRepository.save(Issue.createFromBug(bug, BigDecimal.ONE));
		issue.attach(bug, BigDecimal.ONE);
		issueBugRepository.save(IssueBug.create(issue, bug, BigDecimal.ONE));

		BugOccurrence second = report(
				project,
				"Second failure",
				Instant.parse("2026-08-10T00:10:00Z")
		);
		bug.recordOccurrence(second);
		issue.recordOccurrence(bug, second.getOccurredAt());
		return new Fixture(project, issue);
	}

	private BugOccurrence report(Project project, String title, Instant occurredAt) {
		return occurrenceRepository.save(BugOccurrence.collect(
				project,
				BugSource.API,
				title,
				"HTTP 500",
				"IllegalStateException",
				"saved search failed",
				List.of("SavedSearchService.run"),
				OBJECT_MAPPER.createObjectNode(),
				occurredAt
		));
	}

	private record Fixture(Project project, Issue issue) {
	}
}
