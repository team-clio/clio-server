package ax.clio.issue.entity;

import java.time.Instant;

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

	@Column(nullable = false)
	private int bugCount;

	@Column(nullable = false)
	private int occurrenceCount;

	private Instant firstSeenAt;

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
