package ax.clio.bug.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

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
@Table(name = "bug_grouping_decisions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BugGroupingDecision {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "bug_report_id", nullable = false)
	private BugOccurrence bugReport;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private BugGroupingAction action;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "matched_bug_id")
	private Bug matchedBug;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "resulting_bug_id")
	private Bug resultingBug;

	@Column(nullable = false, precision = 5, scale = 4)
	private BigDecimal confidence;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private JsonNode decisionSnapshot;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	public static BugGroupingDecision create(
			BugOccurrence bugReport,
			BugGroupingAction action,
			Bug matchedBug,
			Bug resultingBug,
			BigDecimal confidence,
			JsonNode decisionSnapshot
	) {
		BugGroupingDecision decision = new BugGroupingDecision();
		decision.bugReport = Objects.requireNonNull(bugReport);
		decision.action = Objects.requireNonNull(action);
		decision.matchedBug = matchedBug;
		decision.resultingBug = resultingBug;
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
