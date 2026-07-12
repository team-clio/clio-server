package ax.clio.memory;

import java.time.Instant;

import ax.clio.project.Project;
import ax.clio.report.BugReport;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 분석된 버그 리포트의 임베딩 (Issue Memory / roadmap #8).
 *
 * <p>I1: title+description만 임베딩(쿼리와 대칭). I2: 분석 완료 시 저장. I3: 별도 엔티티(리포트/잡 오염 방지).
 * embedding은 #7과 동일하게 이식적 {@code float[]} 배열 컬럼(H2·Postgres 공통 DDL, 유사도는 pgvector
 * {@code ::vector} 캐스팅). titleSnapshot은 표시용 스냅샷.
 */
@Entity
@Table(name = "issue_embeddings")
public class IssueEmbedding {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "report_id", nullable = false)
	private BugReport report;

	@Column(length = 200)
	private String titleSnapshot;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "embedding")
	private float[] embedding;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected IssueEmbedding() {
	}

	public IssueEmbedding(Project project, BugReport report, String titleSnapshot, float[] embedding) {
		this.project = project;
		this.report = report;
		this.titleSnapshot = titleSnapshot;
		this.embedding = embedding;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public BugReport getReport() {
		return report;
	}

	public String getTitleSnapshot() {
		return titleSnapshot;
	}

	public float[] getEmbedding() {
		return embedding;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
