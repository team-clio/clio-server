package ax.clio.report;

import java.util.List;

import ax.clio.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class BugReportController {

	private final BugReportService bugReportService;

	public BugReportController(BugReportService bugReportService) {
		this.bugReportService = bugReportService;
	}

	@PostMapping
	public ApiResponse<BugReportResponse> create(@Valid @RequestBody BugReportCreateRequest request) {
		return ApiResponse.ok(bugReportService.create(request));
	}

	@GetMapping
	public ApiResponse<List<BugReportResponse>> findAll(@RequestParam(required = false) Long projectId) {
		return ApiResponse.ok(bugReportService.findAll(projectId));
	}

	@GetMapping("/{id}")
	public ApiResponse<BugReportResponse> findById(@PathVariable Long id) {
		return ApiResponse.ok(bugReportService.findById(id));
	}

	@PatchMapping("/{id}/status")
	public ApiResponse<BugReportResponse> updateStatus(@PathVariable Long id,
			@Valid @RequestBody BugReportStatusUpdateRequest request) {
		return ApiResponse.ok(bugReportService.updateStatus(id, request));
	}
}
