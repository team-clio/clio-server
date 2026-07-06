package ax.clio.llm;

import java.util.List;

import ax.clio.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LlmConfigService {

	private final LlmConfigRepository llmConfigRepository;

	public LlmConfigService(LlmConfigRepository llmConfigRepository) {
		this.llmConfigRepository = llmConfigRepository;
	}

	@Transactional
	public LlmConfigResponse create(LlmConfigCreateRequest request) {
		boolean defaultConfig = Boolean.TRUE.equals(request.defaultConfig())
				|| !llmConfigRepository.existsByDefaultConfigTrue();
		if (defaultConfig) {
			clearDefaultConfig();
		}
		LlmConfig config = new LlmConfig(
				request.name(),
				request.provider(),
				normalizeBaseUrl(request.baseUrl()),
				request.apiKey(),
				request.defaultModel(),
				request.enabled() == null || request.enabled(),
				defaultConfig
		);
		return LlmConfigResponse.from(llmConfigRepository.save(config));
	}

	@Transactional(readOnly = true)
	public List<LlmConfigResponse> findAll() {
		return llmConfigRepository.findAllByOrderByDefaultConfigDescNameAsc().stream()
				.map(LlmConfigResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public LlmConfigResponse findById(Long id) {
		return LlmConfigResponse.from(getConfig(id));
	}

	@Transactional
	public LlmConfigResponse update(Long id, LlmConfigUpdateRequest request) {
		LlmConfig config = getConfig(id);
		boolean defaultConfig = Boolean.TRUE.equals(request.defaultConfig());
		if (defaultConfig) {
			clearDefaultConfig();
		}
		config.update(
				request.name(),
				request.provider(),
				normalizeBaseUrl(request.baseUrl()),
				request.apiKey(),
				request.defaultModel(),
				request.enabled() == null || request.enabled(),
				defaultConfig
		);
		return LlmConfigResponse.from(config);
	}

	@Transactional
	public void delete(Long id) {
		llmConfigRepository.delete(getConfig(id));
	}

	@Transactional(readOnly = true)
	public LlmConfig getConfig(Long id) {
		return llmConfigRepository.findById(id)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "LLM config not found"));
	}

	@Transactional(readOnly = true)
	public LlmConfig getDefaultConfig() {
		return llmConfigRepository.findByDefaultConfigTrueAndEnabledTrue()
				.orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "Default LLM config not found"));
	}

	private void clearDefaultConfig() {
		llmConfigRepository.findAll().forEach(config -> config.changeDefaultConfig(false));
	}

	private String normalizeBaseUrl(String baseUrl) {
		String normalized = baseUrl.strip();
		while (normalized.endsWith("/")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		return normalized;
	}
}
