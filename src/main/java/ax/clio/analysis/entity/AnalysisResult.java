package ax.clio.analysis.entity;

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
	@JoinColumn(name = "job_id", nullable = false, unique = true)
	private AnalysisJob job;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private AnalysisResultStatus status;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "previous_analysis_job_id")
	private AnalysisJob previousAnalysisJob;

	/** Agent의 완전한 IssueAnalysis 계약을 분석 작업별 immutable snapshot으로 보존한다. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private JsonNode resultSnapshot;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	public static AnalysisResult create(
			AnalysisJob job,
			AnalysisResultStatus status,
			AnalysisJob previousAnalysisJob,
			JsonNode resultSnapshot
	) {
		AnalysisResult result = new AnalysisResult();
		result.job = Objects.requireNonNull(job);
		result.status = Objects.requireNonNull(status);
		result.previousAnalysisJob = previousAnalysisJob;
		result.resultSnapshot = Objects.requireNonNull(resultSnapshot).deepCopy();
		return result;
	}

	public JsonNode getResultSnapshot() {
		return resultSnapshot.deepCopy();
	}

	@PrePersist
	void prePersist() {
		this.createdAt = Instant.now();
	}
}
