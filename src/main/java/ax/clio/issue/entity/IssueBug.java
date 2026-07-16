package ax.clio.issue.entity;

import java.math.BigDecimal;
import java.time.Instant;

import ax.clio.bug.entity.Bug;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
		name = "issue_bugs",
		uniqueConstraints = @UniqueConstraint(name = "uk_issue_bugs_issue_bug", columnNames = {"issue_id", "bug_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssueBug {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "issue_id", nullable = false)
	private Issue issue;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "bug_id", nullable = false)
	private Bug bug;

	private BigDecimal confidence;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private IssueGroupingMethod groupedBy;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void prePersist() {
		this.createdAt = Instant.now();
	}
}
