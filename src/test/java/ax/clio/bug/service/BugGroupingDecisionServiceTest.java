package ax.clio.bug.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import ax.clio.bug.dto.ApplyBugGroupingDecisionRequest;
import ax.clio.bug.dto.BugGroupingDecisionRequest;
import ax.clio.bug.dto.BugGroupingDecisionResponse;
import ax.clio.bug.entity.Bug;
import ax.clio.bug.entity.BugGroupingAction;
import ax.clio.bug.entity.BugOccurrence;
import ax.clio.bug.entity.BugSource;
import ax.clio.bug.repository.BugGroupingDecisionRepository;
import ax.clio.bug.repository.BugOccurrenceRepository;
import ax.clio.bug.repository.BugRepository;
import ax.clio.common.ConflictException;
import ax.clio.issue.entity.Issue;
import ax.clio.issue.entity.IssueBug;
import ax.clio.issue.repository.IssueBugRepository;
import ax.clio.issue.repository.IssueRepository;
import ax.clio.project.entity.Project;
import ax.clio.project.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class BugGroupingDecisionServiceTest {

	private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
			new com.fasterxml.jackson.databind.ObjectMapper();

	@Autowired
	private BugGroupingDecisionService service;

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
	private BugGroupingDecisionRepository decisionRepository;

	@Test
	void agentCanCreateNewBugForUngroupedReport() {
		Fixture fixture = fixture();
		BugOccurrence report = ungroupedReport(fixture.project(), "new failure");

		BugGroupingDecisionResponse response = service.apply(
				fixture.project().getId(),
				report.getId(),
				request(report, BugGroupingAction.CREATE_NEW, null, "REQ-NEW")
		);

		assertThat(response.resultingBugId()).isNotNull();
		assertThat(response.readyForIssueMatching()).isTrue();
		assertThat(report.getBug().getId()).isEqualTo(response.resultingBugId());
		assertThat(report.getBug().getOccurrenceCount()).isEqualTo(1);
	}

	@Test
	void agentCanAttachReportToExistingBugAndUpdateIssueOccurrence() {
		Fixture fixture = fixture();
		BugOccurrence report = ungroupedReport(fixture.project(), "same failure again");

		BugGroupingDecisionResponse response = service.apply(
				fixture.project().getId(),
				report.getId(),
				request(
						report,
						BugGroupingAction.MATCH_EXISTING,
						fixture.bug().getId(),
						"REQ-MATCH"
				)
		);

		assertThat(response.resultingBugId()).isEqualTo(fixture.bug().getId());
		assertThat(response.resultingIssueId()).isEqualTo(fixture.issue().getId());
		assertThat(response.readyForIssueMatching()).isFalse();
		assertThat(report.getBug()).isEqualTo(fixture.bug());
		assertThat(fixture.bug().getOccurrenceCount()).isEqualTo(2);
		assertThat(fixture.issue().getBugCount()).isEqualTo(1);
		assertThat(fixture.issue().getOccurrenceCount()).isEqualTo(2);
	}

	@Test
	void reviewRecordsDecisionWithoutGroupingReport() {
		Fixture fixture = fixture();
		BugOccurrence report = ungroupedReport(fixture.project(), "unclear failure");

		BugGroupingDecisionResponse response = service.apply(
				fixture.project().getId(),
				report.getId(),
				request(report, BugGroupingAction.REVIEW, fixture.bug().getId(), "REQ-REVIEW")
		);

		assertThat(response.resultingBugId()).isNull();
		assertThat(response.readyForIssueMatching()).isFalse();
		assertThat(report.getBug()).isNull();
		assertThat(decisionRepository.count()).isEqualTo(1);
	}

	@Test
	void rejectsSecondGroupingOfSameReport() {
		Fixture fixture = fixture();
		BugOccurrence report = ungroupedReport(fixture.project(), "new failure");
		service.apply(
				fixture.project().getId(),
				report.getId(),
				request(report, BugGroupingAction.CREATE_NEW, null, "REQ-FIRST")
		);

		assertThatThrownBy(() -> service.apply(
				fixture.project().getId(),
				report.getId(),
				request(report, BugGroupingAction.CREATE_NEW, null, "REQ-SECOND")
		)).isInstanceOf(ConflictException.class);
	}

	private Fixture fixture() {
		Project project = projectRepository.save(Project.create("Clio", null));
		BugOccurrence first = ungroupedReport(project, "saved search failed");
		Bug bug = bugRepository.save(Bug.createFrom(first));
		Issue issue = issueRepository.save(Issue.createFromBug(bug, BigDecimal.ONE));
		issue.attach(bug, BigDecimal.ONE);
		issueBugRepository.save(IssueBug.create(issue, bug, BigDecimal.ONE));
		return new Fixture(project, bug, issue);
	}

	private BugOccurrence ungroupedReport(Project project, String message) {
		return occurrenceRepository.save(BugOccurrence.collect(
				project,
				BugSource.API,
				"Saved search fails",
				"HTTP 500",
				"IllegalStateException",
				message,
				List.of("SavedSearchService.run"),
				OBJECT_MAPPER.createObjectNode().put("status", 500),
				Instant.parse("2026-08-10T00:00:00Z")
		));
	}

	private ApplyBugGroupingDecisionRequest request(
			BugOccurrence report,
			BugGroupingAction action,
			Long matchedBugId,
			String requestId
	) {
		return new ApplyBugGroupingDecisionRequest(
				requestId,
				new BugGroupingDecisionRequest(
						report.getId(),
						action,
						matchedBugId,
						new BigDecimal("0.9700"),
						List.of("same normalized behavior"),
						List.of(),
						List.of(),
						List.of()
				)
		);
	}

	private record Fixture(Project project, Bug bug, Issue issue) {
	}
}
