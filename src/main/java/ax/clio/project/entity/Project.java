package ax.clio.project.entity;

import java.time.Instant;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "projects")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(nullable = false, unique = true, length = 120)
	private String normalizedName;

	@Column(length = 1000)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ProjectStatus status;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	public static Project create(String name, String description) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Project name must not be blank.");
		}
		Project project = new Project();
		project.name = name.trim();
		project.normalizedName = normalizeName(name);
		project.description = description;
		project.status = ProjectStatus.ACTIVE;
		return project;
	}

	public static String normalizeName(String name) {
		return name.trim().toLowerCase(Locale.ROOT);
	}

	public void update(String name, String description) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Project name must not be blank.");
		}
		this.name = name.trim();
		this.normalizedName = normalizeName(name);
		this.description = description == null || description.isBlank() ? null : description.trim();
	}

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void preUpdate() {
		this.updatedAt = Instant.now();
	}
}
