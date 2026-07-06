package ax.clio.llm;

import java.util.List;

import ax.clio.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/llm-configs")
public class LlmConfigController {

	private final LlmConfigService llmConfigService;

	public LlmConfigController(LlmConfigService llmConfigService) {
		this.llmConfigService = llmConfigService;
	}

	@PostMapping
	public ApiResponse<LlmConfigResponse> create(@Valid @RequestBody LlmConfigCreateRequest request) {
		return ApiResponse.ok(llmConfigService.create(request));
	}

	@GetMapping
	public ApiResponse<List<LlmConfigResponse>> findAll() {
		return ApiResponse.ok(llmConfigService.findAll());
	}

	@GetMapping("/{id}")
	public ApiResponse<LlmConfigResponse> findById(@PathVariable Long id) {
		return ApiResponse.ok(llmConfigService.findById(id));
	}

	@PutMapping("/{id}")
	public ApiResponse<LlmConfigResponse> update(@PathVariable Long id,
			@Valid @RequestBody LlmConfigUpdateRequest request) {
		return ApiResponse.ok(llmConfigService.update(id, request));
	}

	@DeleteMapping("/{id}")
	public ApiResponse<Void> delete(@PathVariable Long id) {
		llmConfigService.delete(id);
		return ApiResponse.ok();
	}
}
