package ax.clio.analysis.job.controller;

import ax.clio.analysis.job.dto.AnalysisJobCreateRequest;
import ax.clio.analysis.job.dto.AnalysisJobResponse;
import ax.clio.analysis.job.service.AnalysisJobService;

import java.util.List;

import ax.clio.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AnalysisJobController {

	private final AnalysisJobService analysisJobService;

	public AnalysisJobController(AnalysisJobService analysisJobService) {
		this.analysisJobService = analysisJobService;
	}

	@PostMapping("/reports/{reportId}/analysis-jobs")
	public ApiResponse<AnalysisJobResponse> create(@PathVariable Long reportId,
			@RequestBody(required = false) AnalysisJobCreateRequest request) {
		return ApiResponse.ok(analysisJobService.create(reportId, request));
	}

	@GetMapping("/reports/{reportId}/analysis-jobs")
	public ApiResponse<List<AnalysisJobResponse>> findByReportId(@PathVariable Long reportId) {
		return ApiResponse.ok(analysisJobService.findByReportId(reportId));
	}

	@GetMapping("/analysis-jobs/{id}")
	public ApiResponse<AnalysisJobResponse> findById(@PathVariable Long id) {
		return ApiResponse.ok(analysisJobService.findById(id));
	}
}
