package ax.clio.issue.controller;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ax.clio.common.dto.PageResponse;
import ax.clio.issue.dto.IssueDetailResponse;
import ax.clio.issue.dto.IssueStatsResponse;
import ax.clio.issue.dto.IssueSummaryResponse;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/issues")
public class IssueController {

	@GetMapping
	public ResponseEntity<PageResponse<IssueSummaryResponse>> getIssues(
			@PathVariable Long projectId,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String priority,
			@RequestParam(required = false) String severity,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "lastSeenAt,desc") String sort
	) {
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
	}

	@GetMapping("/{issueId}")
	public ResponseEntity<IssueDetailResponse> getIssue(
			@PathVariable Long projectId,
			@PathVariable Long issueId
	) {
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
	}

	@GetMapping("/stats")
	public ResponseEntity<IssueStatsResponse> getIssueStats(
			@PathVariable Long projectId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
	) {
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
	}
}
