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
import jakarta.persistence.Lob;
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
@Table(
		name = "project_contexts",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_project_context_project_content_hash",
				columnNames = {"project_id", "content_hash"}
		)
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectContext {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Column(nullable = false, length = 200)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private ProjectContextType type;

	@Lob
	@Column(nullable = false)
	private String content;

	@Column(length = 1000)
	private String sourcePath;

	@Column(length = 1000)
	private String sourceUrl;

	@Column(name = "original_filename", nullable = false, length = 500)
	private String originalFilename;

	@Column(name = "media_type", nullable = false, length = 100)
	private String mediaType;

	@Column(name = "content_hash", nullable = false, length = 71)
	private String contentHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "sync_status", nullable = false, length = 20)
	private ProjectDocumentSyncStatus syncStatus;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	public static ProjectContext createDocument(
			Project project,
			String title,
			String markdown,
			String originalFilename,
			String mediaType,
			String contentHash
	) {
		ProjectContext context = new ProjectContext();
		context.project = project;
		context.title = title.trim();
		context.type = ProjectContextType.ETC;
		context.content = markdown;
		context.originalFilename = originalFilename;
		context.mediaType = mediaType;
		context.contentHash = contentHash;
		context.syncStatus = ProjectDocumentSyncStatus.PENDING;
		return context;
	}

	public void markSyncing() {
		this.syncStatus = ProjectDocumentSyncStatus.SYNCING;
	}

	public void markSynced() {
		this.syncStatus = ProjectDocumentSyncStatus.SYNCED;
	}

	public void markFailed() {
		this.syncStatus = ProjectDocumentSyncStatus.FAILED;
	}

	public void markDeleting() {
		this.syncStatus = ProjectDocumentSyncStatus.DELETING;
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
