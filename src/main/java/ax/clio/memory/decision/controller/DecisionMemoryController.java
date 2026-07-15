package ax.clio.memory.decision.controller;

import java.util.List;

import ax.clio.common.ApiResponse;
import ax.clio.memory.decision.dto.DecisionMemoryCreateRequest;
import ax.clio.memory.decision.entity.DecisionMemory;
import ax.clio.memory.decision.dto.DecisionMemoryResponse;
import ax.clio.memory.decision.service.DecisionMemoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 설계 결정 메모 등록·조회 (Decision Memory / roadmap #9, D2: 생성 + 프로젝트별 목록).
 */
@RestController
@RequestMapping("/api/decisions")
public class DecisionMemoryController {

	private final DecisionMemoryService decisionMemoryService;

	public DecisionMemoryController(DecisionMemoryService decisionMemoryService) {
		this.decisionMemoryService = decisionMemoryService;
	}

	@PostMapping
	public ApiResponse<DecisionMemoryResponse> create(@Valid @RequestBody DecisionMemoryCreateRequest request) {
		DecisionMemory decision = decisionMemoryService.register(request.projectId(), request.title(), request.body());
		return ApiResponse.ok(DecisionMemoryResponse.from(decision));
	}

	@GetMapping
	public ApiResponse<List<DecisionMemoryResponse>> findByProject(@RequestParam Long projectId) {
		List<DecisionMemoryResponse> decisions = decisionMemoryService.findByProject(projectId).stream()
				.map(DecisionMemoryResponse::from)
				.toList();
		return ApiResponse.ok(decisions);
	}
}
