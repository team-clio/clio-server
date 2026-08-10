package ax.clio.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import ax.clio.analysis.dto.AnalysisJobContextResponse;
import ax.clio.analysis.dto.AnalysisJobResponse;
import ax.clio.analysis.dto.AnalysisResultResponse;
import ax.clio.analysis.dto.CreateAnalysisJobRequest;
import ax.clio.analysis.dto.SaveAnalysisResultRequest;
import ax.clio.analysis.dto.UpdateAnalysisJobRequest;
import ax.clio.analysis.entity.AnalysisJobStatus;
import ax.clio.analysis.entity.AnalysisResultStatus;
import ax.clio.analysis.entity.SearchMode;
import ax.clio.analysis.repository.AnalysisJobRepository;
import ax.clio.analysis.repository.AnalysisResultRepository;
import ax.clio.bug.entity.Bug;
import ax.clio.bug.entity.BugOccurrence;
import ax.clio.bug.entity.BugSource;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Transactional
class AnalysisJobServiceTest {

	private static final com.fasterxml.jackson.databind.ObjectMapper PERSISTENCE_MAPPER =
			new com.fasterxml.jackson.databind.ObjectMapper();

	@Autowired
	private AnalysisJobService service;

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

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void createsPendingHybridJobAndReturnsContext() {
		Fixture fixture = fixture();

		AnalysisJobResponse created = service.create(
				fixture.project().getId(),
				fixture.issue().getId(),
				new CreateAnalysisJobRequest("REQ-CREATE", fixture.bug().getId(), null)
		);
		AnalysisJobContextResponse context = service.getContext(
				fixture.project().getId(),
				created.analysisJobId()
		);

		assertThat(created.status()).isEqualTo(AnalysisJobStatus.PENDING);
		assertThat(jobRepository.findById(created.analysisJobId()).orElseThrow().getSearchMode())
				.isEqualTo(SearchMode.HYBRID);
		assertThat(context.triggerBugId()).isEqualTo(fixture.bug().getId());
		assertThat(context.bugs()).singleElement().satisfies(bug -> {
			assertThat(bug.bugId()).isEqualTo(fixture.bug().getId());
			assertThat(bug.latestBugReportId()).isEqualTo(fixture.report().getId());
		});
		assertThat(context.previousAnalysis()).isNull();
	}

	@Test
	void startsAndAtomicallyCompletesJobWithResult() {
		Fixture fixture = fixture();
		AnalysisJobResponse created = createInitial(fixture);

		AnalysisJobResponse running = service.update(
				fixture.project().getId(),
				created.analysisJobId(),
				new UpdateAnalysisJobRequest("REQ-START", AnalysisJobStatus.RUNNING, null)
		);
		AnalysisResultResponse result = service.saveResult(
				fixture.project().getId(),
				created.analysisJobId(),
				new SaveAnalysisResultRequest(
						"REQ-RESULT",
						analysisSnapshot(created, fixture, AnalysisResultStatus.COMPLETED, null)
				)
		);

		assertThat(running.status()).isEqualTo(AnalysisJobStatus.RUNNING);
		assertThat(result.jobStatus()).isEqualTo(AnalysisJobStatus.COMPLETED);
		assertThat(result.resultStatus()).isEqualTo(AnalysisResultStatus.COMPLETED);
		assertThat(resultRepository.findByJobId(created.analysisJobId())).isPresent();
		assertThat(jobRepository.findById(created.analysisJobId()).orElseThrow().getCompletedAt())
				.isNotNull();
	}

	@Test
	void insufficientEvidenceIsAlsoACompletedJob() {
		Fixture fixture = fixture();
		AnalysisJobResponse created = createInitial(fixture);
		service.update(
				fixture.project().getId(),
				created.analysisJobId(),
				new UpdateAnalysisJobRequest("REQ-START", AnalysisJobStatus.RUNNING, null)
		);

		AnalysisResultResponse result = service.saveResult(
				fixture.project().getId(),
				created.analysisJobId(),
				new SaveAnalysisResultRequest(
						"REQ-RESULT",
						analysisSnapshot(
								created,
								fixture,
								AnalysisResultStatus.INSUFFICIENT_EVIDENCE,
								null
						)
				)
		);

		assertThat(result.jobStatus()).isEqualTo(AnalysisJobStatus.COMPLETED);
		assertThat(result.resultStatus()).isEqualTo(AnalysisResultStatus.INSUFFICIENT_EVIDENCE);
	}

	@Test
	void rejectsResultWhoseIdentityDoesNotMatchJob() {
		Fixture fixture = fixture();
		AnalysisJobResponse created = createInitial(fixture);
		service.update(
				fixture.project().getId(),
				created.analysisJobId(),
				new UpdateAnalysisJobRequest("REQ-START", AnalysisJobStatus.RUNNING, null)
		);
		JsonNode invalid = analysisSnapshot(created, fixture, AnalysisResultStatus.COMPLETED, null);
		((tools.jackson.databind.node.ObjectNode) invalid).put("issue_id", fixture.issue().getId() + 1);

		assertThatThrownBy(() -> service.saveResult(
				fixture.project().getId(),
				created.analysisJobId(),
				new SaveAnalysisResultRequest("REQ-RESULT", invalid)
		)).isInstanceOf(ConflictException.class)
				.hasMessageContaining("issue_id");
		assertThat(resultRepository.findByJobId(created.analysisJobId())).isEmpty();
	}

	@Test
	void reanalysisRequiresCompletedPreviousResultAndReturnsItsSnapshot() {
		Fixture fixture = fixture();
		AnalysisJobResponse previous = createInitial(fixture);

		assertThatThrownBy(() -> service.create(
				fixture.project().getId(),
				fixture.issue().getId(),
				new CreateAnalysisJobRequest("REQ-EARLY", fixture.bug().getId(), previous.analysisJobId())
		)).isInstanceOf(ConflictException.class);

		service.update(
				fixture.project().getId(),
				previous.analysisJobId(),
				new UpdateAnalysisJobRequest("REQ-START", AnalysisJobStatus.RUNNING, null)
		);
		service.saveResult(
				fixture.project().getId(),
				previous.analysisJobId(),
				new SaveAnalysisResultRequest(
						"REQ-RESULT",
						analysisSnapshot(previous, fixture, AnalysisResultStatus.COMPLETED, null)
				)
		);

		AnalysisJobResponse revision = service.create(
				fixture.project().getId(),
				fixture.issue().getId(),
				new CreateAnalysisJobRequest(
						"REQ-REVISION",
						fixture.bug().getId(),
						previous.analysisJobId()
				)
		);
		AnalysisJobContextResponse context = service.getContext(
				fixture.project().getId(),
				revision.analysisJobId()
		);

		assertThat(context.previousAnalysis().get("analysis_job_id").asLong())
				.isEqualTo(previous.analysisJobId());
	}

	@Test
	void onlyAllowsDeclaredStatusTransitions() {
		Fixture fixture = fixture();
		AnalysisJobResponse created = createInitial(fixture);
		service.update(
				fixture.project().getId(),
				created.analysisJobId(),
				new UpdateAnalysisJobRequest("REQ-FAIL", AnalysisJobStatus.FAILED, "agent timeout")
		);

		assertThatThrownBy(() -> service.update(
				fixture.project().getId(),
				created.analysisJobId(),
				new UpdateAnalysisJobRequest("REQ-RESTART", AnalysisJobStatus.RUNNING, null)
		)).isInstanceOf(ConflictException.class);
	}

	private AnalysisJobResponse createInitial(Fixture fixture) {
		return service.create(
				fixture.project().getId(),
				fixture.issue().getId(),
				new CreateAnalysisJobRequest("REQ-CREATE", fixture.bug().getId(), null)
		);
	}

	private JsonNode analysisSnapshot(
			AnalysisJobResponse job,
			Fixture fixture,
			AnalysisResultStatus status,
			Long previousJobId
	) {
		var snapshot = objectMapper.createObjectNode()
				.put("analysis_job_id", job.analysisJobId())
				.put("project_id", fixture.project().getId())
				.put("issue_id", fixture.issue().getId())
				.put("status", status.name());
		if (previousJobId != null) {
			snapshot.putObject("revision_summary")
					.put("previous_analysis_job_id", previousJobId);
		}
		return snapshot;
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
				java.util.List.of("SavedSearchService.run"),
				PERSISTENCE_MAPPER.createObjectNode().put("status", 500),
				occurredAt
		);
		Bug bug = bugRepository.save(Bug.createFrom(report));
		report = occurrenceRepository.save(report);
		Issue issue = issueRepository.save(Issue.createFromBug(bug, BigDecimal.ONE));
		issue.attach(bug, BigDecimal.ONE);
		issueBugRepository.save(IssueBug.create(issue, bug, BigDecimal.ONE));
		return new Fixture(project, bug, report, issue);
	}

	private record Fixture(Project project, Bug bug, BugOccurrence report, Issue issue) {
	}
}
