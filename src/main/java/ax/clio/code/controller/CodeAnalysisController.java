package ax.clio.code.controller;

import ax.clio.code.dto.CodeFileResponse;
import ax.clio.code.dto.CodeSymbolResponse;
import ax.clio.code.entity.CodeScanResult;
import ax.clio.code.service.CodeAnalysisService;

import java.util.List;

import ax.clio.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class CodeAnalysisController {

	private final CodeAnalysisService codeAnalysisService;

	public CodeAnalysisController(CodeAnalysisService codeAnalysisService) {
		this.codeAnalysisService = codeAnalysisService;
	}

	@PostMapping("/scan")
	public ApiResponse<CodeScanResult> scan(@PathVariable Long projectId) {
		return ApiResponse.ok(codeAnalysisService.scan(projectId));
	}

	@GetMapping("/files")
	public ApiResponse<List<CodeFileResponse>> findFiles(@PathVariable Long projectId) {
		return ApiResponse.ok(codeAnalysisService.findFiles(projectId));
	}

	@GetMapping("/symbols")
	public ApiResponse<List<CodeSymbolResponse>> findSymbols(@PathVariable Long projectId) {
		return ApiResponse.ok(codeAnalysisService.findSymbols(projectId));
	}
}
