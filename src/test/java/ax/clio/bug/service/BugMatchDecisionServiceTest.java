package ax.clio.bug.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import ax.clio.bug.dto.ApplyMatchDecisionRequest;
import ax.clio.bug.dto.MatchDecisionRequest;
import ax.clio.bug.dto.MatchDecisionResponse;
import ax.clio.bug.entity.Bug;
import ax.clio.bug.entity.BugMatchAction;
import ax.clio.bug.entity.BugOccurrence;
import ax.clio.bug.entity.BugSource;
import ax.clio.bug.entity.BugStatus;
import ax.clio.bug.repository.BugMatchDecisionRepository;
import ax.clio.bug.repository.BugOccurrenceRepository;
import ax.clio.bug.repository.BugRepository;
import ax.clio.common.ConflictException;
import ax.clio.issue.entity.Issue;
import ax.clio.issue.repository.IssueBugRepository;
import ax.clio.issue.repository.IssueRepository;
import ax.clio.project.entity.Project;
import ax.clio.project.repository.ProjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class BugMatchDecisionServiceTest {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Autowired
	private BugMatchDecisionService service;

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
	void autoLinksBugToExistingIssue() {
		Fixture fixture = fixture();
		Issue issue = issueRepository.save(Issue.createFromBug(fixture.bug(), BigDecimal.ONE));

		MatchDecisionResponse response = service.apply(
				fixture.project().getId(),
				fixture.bug().getId(),
				request(fixture, BugMatchAction.AUTO_LINK, issue.getId(), "REQ-AUTO")
		);

		assertThat(response.resultingIssueId()).isEqualTo(issue.getId());
		assertThat(response.linked()).isTrue();
		assertThat(issueBugRepository.findByBugId(fixture.bug().getId())).isPresent();
		assertThat(issue.getBugCount()).isEqualTo(1);
		assertThat(issue.getOccurrenceCount()).isEqualTo(1);
		assertThat(fixture.bug().getStatus()).isEqualTo(BugStatus.TRIAGED);
	}

	@Test
	void reviewOnlyRecordsDecision() {
		Fixture fixture = fixture();
		Issue candidate = issueRepository.save(Issue.createFromBug(fixture.bug(), BigDecimal.ONE));

		MatchDecisionResponse response = service.apply(
				fixture.project().getId(),
				fixture.bug().getId(),
				request(fixture, BugMatchAction.REVIEW, candidate.getId(), "REQ-REVIEW")
		);

		assertThat(response.linked()).isFalse();
		assertThat(response.resultingIssueId()).isNull();
		assertThat(issueBugRepository.findByBugId(fixture.bug().getId())).isEmpty();
		assertThat(fixture.bug().getStatus()).isEqualTo(BugStatus.NEW);
		assertThat(decisionRepository.count()).isEqualTo(1);
	}

	@Test
	void createsNewIssueAndLinksBug() {
		Fixture fixture = fixture();

		MatchDecisionResponse response = service.apply(
				fixture.project().getId(),
				fixture.bug().getId(),
				request(fixture, BugMatchAction.CREATE_NEW, null, "REQ-CREATE")
		);

		assertThat(response.resultingIssueId()).isNotNull();
		assertThat(response.linked()).isTrue();
		assertThat(issueRepository.count()).isEqualTo(1);
		assertThat(issueBugRepository.findByBugId(fixture.bug().getId())).isPresent();
	}

	@Test
	void reusesSameLinkButRejectsDifferentIssue() {
		Fixture fixture = fixture();
		Issue first = issueRepository.save(Issue.createFromBug(fixture.bug(), BigDecimal.ONE));
		Issue second = issueRepository.save(Issue.createFromBug(fixture.bug(), BigDecimal.ONE));
		service.apply(
				fixture.project().getId(),
				fixture.bug().getId(),
				request(fixture, BugMatchAction.AUTO_LINK, first.getId(), "REQ-FIRST")
		);
		service.apply(
				fixture.project().getId(),
				fixture.bug().getId(),
				request(fixture, BugMatchAction.AUTO_LINK, first.getId(), "REQ-SAME")
		);

		assertThat(first.getBugCount()).isEqualTo(1);
		assertThat(issueBugRepository.count()).isEqualTo(1);
		assertThatThrownBy(() -> service.apply(
				fixture.project().getId(),
				fixture.bug().getId(),
				request(fixture, BugMatchAction.AUTO_LINK, second.getId(), "REQ-DIFFERENT")
		)).isInstanceOf(ConflictException.class);
	}

	@Test
	void agentDtoUsesSnakeCaseJsonWithoutChangingJavaNames() {
		Fixture fixture = fixture();
		ApplyMatchDecisionRequest request = request(
				fixture,
				BugMatchAction.AUTO_LINK,
				23L,
				"REQ-JSON"
		);

		String json = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(request);

		assertThat(json).contains("\"request_id\"", "\"bug_report_id\"", "\"match_decision\"");
		assertThat(json).contains("\"matched_issue_id\"");
		assertThat(json).doesNotContain("requestId", "matchedIssueId");
	}

	private Fixture fixture() {
		Project project = projectRepository.save(Project.create("Clio", null));
		Instant occurredAt = Instant.parse("2026-08-10T00:00:00Z");
		BugOccurrence report = BugOccurrence.collect(
				project,
				BugSource.API,
				"Saved search fails",
				"HTTP 500",
				"IllegalStateException",
				"saved search failed",
				List.of("SavedSearchService.run"),
				OBJECT_MAPPER.createObjectNode().put("status", 500),
				occurredAt
		);
		Bug bug = bugRepository.save(Bug.createFrom(report));
		report = occurrenceRepository.save(report);
		return new Fixture(project, bug, report);
	}

	private ApplyMatchDecisionRequest request(
			Fixture fixture,
			BugMatchAction action,
			Long matchedIssueId,
			String requestId
	) {
		return new ApplyMatchDecisionRequest(
				requestId,
				fixture.report().getId(),
				new MatchDecisionRequest(
						fixture.bug().getId(),
						action,
						matchedIssueId,
						new BigDecimal("0.9700"),
						List.of("same error"),
						List.of(),
						List.of(),
						List.of()
				)
		);
	}

	private record Fixture(Project project, Bug bug, BugOccurrence report) {
	}
}
