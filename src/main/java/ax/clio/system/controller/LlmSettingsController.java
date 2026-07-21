package ax.clio.system.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ax.clio.common.dto.ListResponse;
import ax.clio.system.dto.CurrentLlmSettingsResponse;
import ax.clio.system.dto.LlmConnectionTestRequest;
import ax.clio.system.dto.LlmConnectionTestResponse;
import ax.clio.system.dto.LlmModelResponse;
import ax.clio.system.dto.LlmProviderResponse;
import ax.clio.system.dto.UpdateLlmModelsRequest;
import ax.clio.system.dto.UpdateLlmProviderRequest;

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
}
