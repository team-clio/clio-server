package ax.clio.analysis.entity;

import java.time.Instant;
import java.util.Objects;

import ax.clio.issue.entity.Issue;
import ax.clio.workflow.entity.AgentWorkflowRun;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "analysis_results")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisResult {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "workflow_run_id", nullable = false, unique = true)
	private AgentWorkflowRun workflowRun;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "issue_id", nullable = false)
	private Issue issue;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "previous_analysis_result_id")
	private AnalysisResult previousAnalysisResult;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private AnalysisResultStatus status;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private JsonNode resultSnapshot;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	public static AnalysisResult create(
			AgentWorkflowRun workflowRun,
			Issue issue,
			AnalysisResult previous,
			AnalysisResultStatus status,
			JsonNode snapshot
	) {
		AnalysisResult result = new AnalysisResult();
		result.workflowRun = Objects.requireNonNull(workflowRun);
		result.issue = Objects.requireNonNull(issue);
		result.previousAnalysisResult = previous;
		result.status = Objects.requireNonNull(status);
		result.resultSnapshot = Objects.requireNonNull(snapshot).deepCopy();
		return result;
	}

	public JsonNode getResultSnapshot() {
		return resultSnapshot.deepCopy();
	}

	@PrePersist
	void prePersist() {
		createdAt = Instant.now();
	}
}
