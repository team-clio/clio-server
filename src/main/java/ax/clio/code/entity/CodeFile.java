package ax.clio.code.entity;

import java.time.Instant;

import ax.clio.project.entity.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
		name = "code_files",
		uniqueConstraints = @UniqueConstraint(name = "uk_code_files_project_path", columnNames = {"project_id", "path"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CodeFile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Column(nullable = false, length = 1200)
	private String path;

	@Column(nullable = false, length = 255)
	private String fileName;

	@Column(nullable = false, length = 30)
	private String language;

	@Column(nullable = false)
	private boolean test;

	@Column(nullable = false)
	private long sizeBytes;

	@Column(length = 128)
	private String contentHash;

	@Column(nullable = false)
	private Instant lastModifiedAt;

	private Instant indexedAt;
}
