package ax.clio.bug.controller;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ax.clio.bug.dto.AgentBugReportResponse;
import ax.clio.bug.dto.BugReportCollectRequest;
import ax.clio.bug.dto.BugReportResponse;
import ax.clio.bug.dto.BugReportSummaryResponse;
import ax.clio.common.dto.PageResponse;
import ax.clio.bug.entity.BugSource;
import ax.clio.bug.entity.BugStatus;
import ax.clio.bug.entity.Severity;
import ax.clio.bug.service.BugReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/bug-reports")
@RequiredArgsConstructor
@Validated
public class BugReportController {

	private final BugReportService bugReportService;

	@PostMapping
	public ResponseEntity<BugReportResponse> collectBugReport(
			@PathVariable Long projectId,
			@Valid @RequestBody BugReportCollectRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(bugReportService.collect(projectId, request));
	}

	@GetMapping("/{reportId}")
	public AgentBugReportResponse getBugReport(
			@PathVariable Long projectId,
			@PathVariable Long reportId
	) {
		return bugReportService.getForAgent(projectId, reportId);
	}

	@GetMapping
	public ResponseEntity<PageResponse<BugReportSummaryResponse>> getBugReports(
			@PathVariable Long projectId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(required = false) Long issueId,
			@RequestParam(required = false) BugSource source,
			@RequestParam(required = false) BugStatus status,
			@RequestParam(required = false) Severity severity,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
	) {
		return ResponseEntity.ok(bugReportService.search(
				projectId,
				from,
				to,
				issueId,
				source,
				status,
				severity,
				page,
				size
		));
	}
}
