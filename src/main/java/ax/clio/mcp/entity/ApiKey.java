package ax.clio.mcp.entity;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
		name = "api_keys",
		uniqueConstraints = @UniqueConstraint(name = "uk_api_keys_key_hash", columnNames = "key_hash")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiKey {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "key_prefix", nullable = false, length = 20)
	private String keyPrefix;

	@Column(name = "key_hash", nullable = false, length = 128)
	private String keyHash;

	@Column(nullable = false)
	private boolean revoked;

	private Instant lastUsedAt;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void prePersist() {
		this.createdAt = Instant.now();
	}
}
