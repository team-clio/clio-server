package ax.clio.issue.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

import ax.clio.bug.entity.Bug;
import ax.clio.bug.entity.Priority;
import ax.clio.bug.entity.Severity;
import ax.clio.project.entity.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "issues")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Issue {
	private static final Map<IssueStatus, EnumSet<IssueStatus>> STATUS_TRANSITIONS = Map.of(
			IssueStatus.OPEN, EnumSet.of(IssueStatus.IN_PROGRESS, IssueStatus.CLOSED),
			IssueStatus.IN_PROGRESS, EnumSet.of(IssueStatus.OPEN, IssueStatus.RESOLVED, IssueStatus.CLOSED),
			IssueStatus.RESOLVED, EnumSet.of(IssueStatus.IN_PROGRESS, IssueStatus.CLOSED),
			IssueStatus.CLOSED, EnumSet.of(IssueStatus.OPEN)
	);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Column(nullable = false, length = 200)
	private String title;

	@Lob
	private String summary;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private IssueStatus status;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private Priority priority;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private Severity severity;

	private Integer importanceScore;

	private Integer riskScore;

	@Column(length = 100)
	private String assigneeName;

	@Column(precision = 5, scale = 4)
	private BigDecimal aiConfidence;

	@Column(nullable = false)
	private int bugCount;

	private Instant firstSeenAt;

	private Instant lastSeenAt;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	public static Issue createFromBug(
			Bug bug, BigDecimal confidence, String title, String description
	) {
		Issue issue = new Issue();
		issue.project = Objects.requireNonNull(bug).getProject();
		issue.title = normalize(title);
		if (issue.title == null) {
			issue.title = bug.displayTitle();
		}
		issue.summary = normalize(description);
		if (issue.summary == null) {
			issue.summary = bug.getDescription();
		}
		issue.status = IssueStatus.OPEN;
		issue.severity = bug.getSeverity();
		issue.aiConfidence = confidence;
		issue.firstSeenAt = bug.getOccurredAt();
		issue.lastSeenAt = bug.getOccurredAt();
		return issue;
	}

	public void attach(Bug bug, BigDecimal confidence) {
		Objects.requireNonNull(bug);
		this.bugCount += 1;
		this.aiConfidence = confidence;
		if (this.firstSeenAt == null || bug.getOccurredAt().isBefore(this.firstSeenAt)) {
			this.firstSeenAt = bug.getOccurredAt();
		}
		if (this.lastSeenAt == null || bug.getOccurredAt().isAfter(this.lastSeenAt)) {
			this.lastSeenAt = bug.getOccurredAt();
		}
	}

	public void updateStatus(IssueStatus nextStatus) {
		Objects.requireNonNull(nextStatus);
		if (status == nextStatus) {
			return;
		}
		if (!STATUS_TRANSITIONS.get(status).contains(nextStatus)) {
			throw new IllegalStateException(
					"Unsupported issue status transition: " + status + " -> " + nextStatus
			);
		}
		this.status = nextStatus;
	}

	public void updateTriage(Priority priority, Severity severity, String assigneeName) {
		this.priority = priority;
		this.severity = severity;
		this.assigneeName = normalize(assigneeName);
	}

	public void applyRiskAssessment(int riskScore, Priority priority) {
		this.riskScore = riskScore;
		this.priority = Objects.requireNonNull(priority);
	}

	private static String normalize(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void preUpdate() {
		this.updatedAt = Instant.now();
	}
}
