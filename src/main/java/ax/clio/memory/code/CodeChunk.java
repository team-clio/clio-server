package ax.clio.memory.code;

import ax.clio.memory.embedding.EmbeddingClient;

import ax.clio.code.entity.CodeFile;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 코드 조각(chunk) + 스캔 시점 스냅샷 본문 + embedding 벡터.
 *
 * <p>D1: 메서드 단위(+클래스 헤더). D2: 본문 DB 저장(길이 상한). D4/D4-1: embedding은 이식적
 * {@code float[]} 배열 컬럼으로 저장(H2·Postgres 공통 DDL). 실제 pgvector 코사인 유사도는
 * 벤치마크 네이티브 쿼리에서 {@code embedding::vector <=> :q::vector}로 캐스팅해 계산한다.
 *
 * <p>D0-B: embed+저장+유사도검색 "메커니즘"({@link EmbeddingClient}, 코사인)은 범용 재사용 가능하게
 * 두되, 이 엔티티 자체는 코드 chunk 전용이다. Issue Memory(#8)는 자체 엔티티에서 같은 메커니즘을 재사용한다.
 */
@Entity
@Table(name = "code_chunks")
public class CodeChunk {

	/** 본문 저장 길이 상한 (D2). */
	public static final int MAX_CONTENT_LENGTH = 8000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "file_id", nullable = false)
	private CodeFile file;

	@Column(nullable = false, length = 500)
	private String path;

	@Column(length = 255)
	private String symbolName;

	@Column(length = 80)
	private String role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private CodeChunkType chunkType;

	private Integer startLine;

	private Integer endLine;

	@Column(length = MAX_CONTENT_LENGTH)
	private String content;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "embedding")
	private float[] embedding;

	protected CodeChunk() {
	}

	public CodeChunk(Project project, CodeFile file, String path, String symbolName, String role,
			CodeChunkType chunkType, Integer startLine, Integer endLine, String content, float[] embedding) {
		this.project = project;
		this.file = file;
		this.path = path;
		this.symbolName = symbolName;
		this.role = role;
		this.chunkType = chunkType;
		this.startLine = startLine;
		this.endLine = endLine;
		this.content = truncate(content);
		this.embedding = embedding;
	}

	/**
	 * embedding을 나중에(스캔 파이프라인의 embed 단계, S3/S4) 부착한다. chunk 본문·메타는 S2에서 확정하고
	 * embedding만 지연 계산하기 위한 뮤테이터.
	 */
	public void assignEmbedding(float[] embedding) {
		this.embedding = embedding;
	}

	private static String truncate(String content) {
		if (content == null || content.length() <= MAX_CONTENT_LENGTH) {
			return content;
		}
		return content.substring(0, MAX_CONTENT_LENGTH);
	}

	public Long getId() {
		return id;
	}

	public CodeFile getFile() {
		return file;
	}

	public String getPath() {
		return path;
	}

	public String getSymbolName() {
		return symbolName;
	}

	public String getRole() {
		return role;
	}

	public CodeChunkType getChunkType() {
		return chunkType;
	}

	public Integer getStartLine() {
		return startLine;
	}

	public Integer getEndLine() {
		return endLine;
	}

	public String getContent() {
		return content;
	}

	public float[] getEmbedding() {
		return embedding;
	}
}
