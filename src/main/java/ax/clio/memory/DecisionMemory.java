package ax.clio.memory;

import java.time.Instant;

import ax.clio.project.Project;
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
 * 사람이 등록한 설계 결정/운영 메모 (Decision Memory / roadmap #9).
 *
 * <p>D1: title+body 최소 스키마. D3: title+body를 임베딩(결정의 의미는 본문에 있음). 리포트와 달리 사람이
 * 직접 등록하므로 report FK가 없고 프로젝트 단위로만 스코프된다. embedding은 #7·#8과 동일하게 이식적
 * {@code float[]} 배열 컬럼(H2·Postgres 공통 DDL, 유사도는 pgvector {@code ::vector} 캐스팅).
 */
@Entity
@Table(name = "decision_memories")
public class DecisionMemory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, length = 10000)
	private String body;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "embedding")
	private float[] embedding;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected DecisionMemory() {
	}

	public DecisionMemory(Project project, String title, String body, float[] embedding) {
		this.project = project;
		this.title = title;
		this.body = body;
		this.embedding = embedding;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Project getProject() {
		return project;
	}

	public String getTitle() {
		return title;
	}

	public String getBody() {
		return body;
	}

	public float[] getEmbedding() {
		return embedding;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
