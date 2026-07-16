package ax.clio.bug.entity;

import java.time.Instant;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
		name = "bugs",
		uniqueConstraints = @UniqueConstraint(name = "uk_bugs_project_fingerprint", columnNames = {"project_id", "fingerprint"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bug {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Column(nullable = false, length = 128)
	private String fingerprint;

	@Lob
	@Column(nullable = false)
	private String fingerprintSource;

	@Column(nullable = false, length = 200)
	private String title;

	@Lob
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private BugSource source;

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
