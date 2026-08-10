package ax.clio.bug.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import ax.clio.issue.entity.Issue;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "bug_match_decisions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BugMatchDecision {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "bug_id", nullable = false)
	private Bug bug;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "bug_report_id", nullable = false)
	private BugOccurrence bugReport;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private BugMatchAction action;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "matched_issue_id")
	private Issue matchedIssue;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "resulting_issue_id")
	private Issue resultingIssue;

	@Column(nullable = false, precision = 5, scale = 4)
	private BigDecimal confidence;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private JsonNode decisionSnapshot;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	public static BugMatchDecision create(
			Bug bug,
			BugOccurrence bugReport,
			BugMatchAction action,
			Issue matchedIssue,
			Issue resultingIssue,
			BigDecimal confidence,
			JsonNode decisionSnapshot
	) {
		BugMatchDecision decision = new BugMatchDecision();
		decision.bug = Objects.requireNonNull(bug);
		decision.bugReport = Objects.requireNonNull(bugReport);
		decision.action = Objects.requireNonNull(action);
		decision.matchedIssue = matchedIssue;
		decision.resultingIssue = resultingIssue;
		decision.confidence = Objects.requireNonNull(confidence);
		decision.decisionSnapshot = Objects.requireNonNull(decisionSnapshot).deepCopy();
		return decision;
	}

	public JsonNode getDecisionSnapshot() {
		return decisionSnapshot.deepCopy();
	}

	@PrePersist
	void prePersist() {
		this.createdAt = Instant.now();
	}
}
