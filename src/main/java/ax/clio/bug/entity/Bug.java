package ax.clio.bug.entity;

import java.time.Instant;
import java.util.Objects;

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
@Table(name = "bugs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bug {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Column(nullable = false, length = 200)
	private String title;

	@Lob
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private BugSource source;

	@Column(length = 100)
	private String reporterName;

	@Column(length = 255)
	private String errorType;

	@Lob
	private String normalizedMessage;

	@Column(length = 1000)
	private String topApplicationFrame;

	@Column(nullable = false)
	private int occurrenceCount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private BugStatus status;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private Severity severity;

	@Column(nullable = false)
	private Instant firstSeenAt;

	@Column(nullable = false)
	private Instant lastSeenAt;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	public static Bug createFrom(BugOccurrence occurrence) {
		Objects.requireNonNull(occurrence);
		if (occurrence.getBug() != null) {
			throw new IllegalStateException("Bug report is already grouped.");
		}
		Bug bug = new Bug();
		bug.project = occurrence.getProject();
		bug.title = occurrence.displayTitle();
		bug.description = occurrence.getDescription();
		bug.source = occurrence.getSource();
		bug.errorType = occurrence.getErrorType();
		bug.normalizedMessage = occurrence.getMessage();
		bug.topApplicationFrame = occurrence.firstStackFrame();
		bug.occurrenceCount = 1;
		bug.status = BugStatus.NEW;
		bug.firstSeenAt = occurrence.getOccurredAt();
		bug.lastSeenAt = occurrence.getOccurredAt();
		occurrence.attachTo(bug);
		return bug;
	}

	public void recordOccurrence(BugOccurrence occurrence) {
		Objects.requireNonNull(occurrence).attachTo(this);
		this.occurrenceCount += 1;
		if (occurrence.getOccurredAt().isBefore(this.firstSeenAt)) {
			this.firstSeenAt = occurrence.getOccurredAt();
		}
		if (occurrence.getOccurredAt().isAfter(this.lastSeenAt)) {
			this.lastSeenAt = occurrence.getOccurredAt();
		}
	}

	public void markTriaged() {
		this.status = BugStatus.TRIAGED;
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
