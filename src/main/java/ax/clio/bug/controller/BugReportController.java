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

import ax.clio.bug.dto.BugReportCollectRequest;
import ax.clio.bug.dto.BugReportResponse;
import ax.clio.bug.dto.BugReportSummaryResponse;
import ax.clio.common.dto.PageResponse;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/bug-reports")
public class BugReportController {

	@PostMapping
	public ResponseEntity<BugReportResponse> collectBugReport(
			@PathVariable Long projectId,
			@RequestBody BugReportCollectRequest request
	) {
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
	}

	@GetMapping
	public ResponseEntity<PageResponse<BugReportSummaryResponse>> getBugReports(
			@PathVariable Long projectId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(required = false) Long issueId,
			@RequestParam(required = false) String source,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String severity,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
	}
}
