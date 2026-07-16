package ax.clio.bug.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "bug_embeddings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BugEmbedding {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "bug_id", nullable = false)
	private Bug bug;

	@JdbcTypeCode(SqlTypes.ARRAY)
	private float[] embedding;

	@Column(length = 200)
	private String embeddingModel;

	private Integer embeddingDimension;

	private Instant embeddedAt;
}
