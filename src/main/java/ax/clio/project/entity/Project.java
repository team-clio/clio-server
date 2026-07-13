package ax.clio.project.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "projects")
public class Project {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(nullable = false, length = 1000)
	private String rootPath;

	@Column(length = 1000)
	private String description;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected Project() {
	}

	public Project(String name, String rootPath, String description) {
		this.name = name;
		this.rootPath = rootPath;
		this.description = description;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getRootPath() {
		return rootPath;
	}

	public String getDescription() {
		return description;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
