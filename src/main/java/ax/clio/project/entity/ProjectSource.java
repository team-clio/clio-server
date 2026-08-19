package ax.clio.project.entity;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "project_sources", uniqueConstraints = @UniqueConstraint(name = "uk_project_sources_project_repo_url", columnNames = {"project_id", "repo_url"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectSource {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private RepositoryProvider provider;

	@Column(nullable = false, length = 255)
	private String owner;

	@Column(nullable = false, length = 255)
	private String name;

	@Column(nullable = false, length = 1000)
	private String repoUrl;

	@Column(nullable = false, length = 255)
	private String targetBranch;

	@Column(length = 1000)
	private String rootPath;

	@Column(columnDefinition = "text")
	private String includePaths;

	@Column(columnDefinition = "text")
	private String excludePaths;

	@Column(nullable = false)
	private boolean enabled;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ProjectSourceSyncStatus syncStatus;

	private Instant lastSyncedAt;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	public static ProjectSource create(
			Project project,
			RepositoryProvider provider,
			String owner,
			String name,
			String repoUrl,
			String targetBranch,
			String includePaths,
			String excludePaths,
			boolean enabled
	) {
		ProjectSource source = new ProjectSource();
		source.project = project;
		source.update(provider, owner, name, repoUrl, targetBranch, includePaths, excludePaths, enabled);
		source.syncStatus = ProjectSourceSyncStatus.PENDING;
		return source;
	}

	public void update(
			RepositoryProvider provider,
			String owner,
			String name,
			String repoUrl,
			String targetBranch,
			String includePaths,
			String excludePaths,
			boolean enabled
	) {
		this.provider = provider;
		this.owner = requireText(owner, "Repository owner");
		this.name = requireText(name, "Repository name");
		this.repoUrl = requireText(repoUrl, "Repository URL");
		this.targetBranch = requireText(targetBranch, "Default branch");
		this.includePaths = includePaths;
		this.excludePaths = excludePaths;
		this.enabled = enabled;
	}

	public void markSyncing() {
		this.syncStatus = ProjectSourceSyncStatus.SYNCING;
	}

	public void markFailed() {
		this.syncStatus = ProjectSourceSyncStatus.FAILED;
	}

	public void markSynced() {
		this.syncStatus = ProjectSourceSyncStatus.SYNCED;
		this.lastSyncedAt = Instant.now();
	}

	public void markPending() {
		this.syncStatus = ProjectSourceSyncStatus.PENDING;
	}

	private static String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank.");
		}
		return value.trim();
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
