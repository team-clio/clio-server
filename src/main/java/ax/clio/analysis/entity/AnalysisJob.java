package ax.clio.analysis.entity;

import java.time.Instant;
import java.util.Objects;

import ax.clio.bug.entity.Bug;
import ax.clio.issue.entity.Issue;
import ax.clio.project.entity.Project;
import ax.clio.system.entity.LlmModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "analysis_jobs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisJob {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "bug_id")
	private Bug bug;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "issue_id")
	private Issue issue;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "previous_analysis_job_id")
	private AnalysisJob previousAnalysisJob;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "llm_model_id")
	private LlmModel llmModel;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private AnalysisJobStatus status;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private SearchMode searchMode;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Instant startedAt;

	private Instant completedAt;

	@Column(length = 1000)
	private String failureReason;

	public static AnalysisJob create(
			Project project,
			Bug triggerBug,
			Issue issue,
			AnalysisJob previousAnalysisJob
	) {
		AnalysisJob job = new AnalysisJob();
		job.project = Objects.requireNonNull(project);
		job.bug = Objects.requireNonNull(triggerBug);
		job.issue = Objects.requireNonNull(issue);
		job.previousAnalysisJob = previousAnalysisJob;
		job.status = AnalysisJobStatus.PENDING;
		job.searchMode = SearchMode.HYBRID;
		return job;
	}

	public void start() {
		if (status != AnalysisJobStatus.PENDING) {
			throw new IllegalStateException("Only a PENDING analysis job can start.");
		}
		status = AnalysisJobStatus.RUNNING;
		startedAt = Instant.now();
	}

	public void fail(String reason) {
		if (status != AnalysisJobStatus.PENDING && status != AnalysisJobStatus.RUNNING) {
			throw new IllegalStateException("Only a PENDING or RUNNING analysis job can fail.");
		}
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("failureReason is required for FAILED status.");
		}
		status = AnalysisJobStatus.FAILED;
		failureReason = reason;
		completedAt = Instant.now();
	}

	public void complete() {
		if (status != AnalysisJobStatus.RUNNING) {
			throw new IllegalStateException("Only a RUNNING analysis job can complete.");
		}
		status = AnalysisJobStatus.COMPLETED;
		completedAt = Instant.now();
	}

	@PrePersist
	void prePersist() {
		this.createdAt = Instant.now();
	}
}
