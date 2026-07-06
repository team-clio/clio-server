package ax.clio.llm;

import java.time.Instant;

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

@Entity
@Table(name = "llm_configs")
public class LlmConfig {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private LlmProvider provider;

	@Column(nullable = false, length = 500)
	private String baseUrl;

	@Column(nullable = false, length = 2000)
	private String apiKey;

	@Column(nullable = false, length = 200)
	private String defaultModel;

	@Column(nullable = false)
	private boolean enabled;

	@Column(nullable = false)
	private boolean defaultConfig;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected LlmConfig() {
	}

	public LlmConfig(String name, LlmProvider provider, String baseUrl, String apiKey, String defaultModel,
			boolean enabled, boolean defaultConfig) {
		this.name = name;
		this.provider = provider;
		this.baseUrl = baseUrl;
		this.apiKey = apiKey;
		this.defaultModel = defaultModel;
		this.enabled = enabled;
		this.defaultConfig = defaultConfig;
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

	public void update(String name, LlmProvider provider, String baseUrl, String apiKey, String defaultModel,
			boolean enabled, boolean defaultConfig) {
		this.name = name;
		this.provider = provider;
		this.baseUrl = baseUrl;
		if (apiKey != null && !apiKey.isBlank()) {
			this.apiKey = apiKey;
		}
		this.defaultModel = defaultModel;
		this.enabled = enabled;
		this.defaultConfig = defaultConfig;
	}

	public void changeDefaultConfig(boolean defaultConfig) {
		this.defaultConfig = defaultConfig;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public LlmProvider getProvider() {
		return provider;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public String getApiKey() {
		return apiKey;
	}

	public String getDefaultModel() {
		return defaultModel;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public boolean isDefaultConfig() {
		return defaultConfig;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
