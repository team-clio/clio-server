package ax.clio.system.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system/llm")
public class LlmSettingsController {

	@GetMapping("/providers")
	public ResponseEntity<ListResponse<LlmProviderResponse>> getProviders() {
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
	}

	@GetMapping("/models")
	public ResponseEntity<ListResponse<LlmModelResponse>> getModels(
			@RequestParam(required = false) String providerType,
			@RequestParam(required = false) String purpose
	) {
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
	}

	@GetMapping("/current")
	public ResponseEntity<CurrentLlmSettingsResponse> getCurrentSettings() {
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
	}

	@PatchMapping("/provider")
	public ResponseEntity<CurrentLlmSettingsResponse> updateProvider(
			@RequestBody UpdateLlmProviderRequest request
	) {
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
	}

	@PatchMapping("/models")
	public ResponseEntity<CurrentLlmSettingsResponse> updateModels(
			@RequestBody UpdateLlmModelsRequest request
	) {
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
	}

	@PostMapping("/test")
	public ResponseEntity<LlmConnectionTestResponse> testConnection(
			@RequestBody LlmConnectionTestRequest request
	) {
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
	}

	public record ListResponse<T>(
			List<T> items
	) {
	}

	public record LlmProviderResponse(
			String type,
			String name,
			String defaultBaseUrl,
			boolean requiresApiKey
	) {
	}

	public record LlmModelResponse(
			String name,
			String providerType,
			String purpose,
			Integer contextWindow,
			Integer embeddingDimension
	) {
	}

	public record CurrentLlmSettingsResponse(
			String providerType,
			String providerName,
			String baseUrl,
			String chatModel,
			String embeddingModel,
			boolean apiKeyConfigured,
			boolean enabled
	) {
	}

	public record UpdateLlmProviderRequest(
			String providerType,
			String baseUrl,
			String apiKey
	) {
	}

	public record UpdateLlmModelsRequest(
			String chatModel,
			String embeddingModel
	) {
	}

	public record LlmConnectionTestRequest(
			String purpose
	) {
	}

	public record LlmConnectionTestResponse(
			boolean success,
			Long latencyMs,
			String message
	) {
	}
}
