package ax.clio.code;

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

@Entity
@Table(name = "code_files")
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

	@Column(name = "is_test", nullable = false)
	private boolean test;

	@Column(nullable = false)
	private long sizeBytes;

	@Column(nullable = false)
	private Instant lastModifiedAt;

	protected CodeFile() {
	}

	public CodeFile(Project project, String path, String fileName, String language, boolean test, long sizeBytes, Instant lastModifiedAt) {
		this.project = project;
		this.path = path;
		this.fileName = fileName;
		this.language = language;
		this.test = test;
		this.sizeBytes = sizeBytes;
		this.lastModifiedAt = lastModifiedAt;
	}

	public Long getId() {
		return id;
	}

	public Project getProject() {
		return project;
	}

	public String getPath() {
		return path;
	}

	public String getFileName() {
		return fileName;
	}

	public String getLanguage() {
		return language;
	}

	public boolean isTest() {
		return test;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public Instant getLastModifiedAt() {
		return lastModifiedAt;
	}
}
