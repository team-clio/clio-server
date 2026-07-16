package ax.clio.code.entity;

import java.time.Instant;

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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "code_chunks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CodeChunk {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "file_id", nullable = false)
	private CodeFile file;

	@Column(nullable = false, length = 1200)
	private String path;

	@Column(length = 255)
	private String symbolName;

	@Column(length = 80)
	private String role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private CodeChunkType chunkType;

	private Integer startLine;

	private Integer endLine;

	@Lob
	private String content;

	@Column(length = 128)
	private String contentHash;

	@JdbcTypeCode(SqlTypes.ARRAY)
	private float[] embedding;

	@Column(length = 200)
	private String embeddingModel;

	private Integer embeddingDimension;

	private Instant embeddedAt;
}
