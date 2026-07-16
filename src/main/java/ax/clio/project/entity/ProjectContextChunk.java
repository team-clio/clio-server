package ax.clio.project.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "project_context_chunks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectContextChunk {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "context_id", nullable = false)
	private ProjectContext context;

	@Column(nullable = false)
	private Integer chunkIndex;

	@Lob
	@Column(nullable = false)
	private String content;

	@JdbcTypeCode(SqlTypes.ARRAY)
	private float[] embedding;

	@Column(length = 200)
	private String embeddingModel;

	private Integer embeddingDimension;

	private Instant embeddedAt;
}
