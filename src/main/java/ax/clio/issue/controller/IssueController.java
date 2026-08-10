package ax.clio.issue.controller;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
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
import ax.clio.bug.entity.Priority;
import ax.clio.bug.entity.Severity;
import ax.clio.issue.entity.IssueStatus;
import ax.clio.issue.service.IssueQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/issues")
@RequiredArgsConstructor
@Validated
public class IssueController {

	private final IssueQueryService issueQueryService;

	@GetMapping
	public ResponseEntity<PageResponse<IssueSummaryResponse>> getIssues(
			@PathVariable Long projectId,
			@RequestParam(required = false) IssueStatus status,
			@RequestParam(required = false) Priority priority,
			@RequestParam(required = false) Severity severity,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@RequestParam(defaultValue = "lastSeenAt,desc") String sort
	) {
		return ResponseEntity.ok(issueQueryService.search(
				projectId,
				status,
				priority,
				severity,
				from,
				to,
				page,
				size,
				sort
		));
	}

	@GetMapping("/{issueId}")
	public ResponseEntity<IssueDetailResponse> getIssue(
			@PathVariable Long projectId,
			@PathVariable Long issueId
	) {
		return ResponseEntity.ok(issueQueryService.get(projectId, issueId));
	}

	@GetMapping("/stats")
	public ResponseEntity<IssueStatsResponse> getIssueStats(
			@PathVariable Long projectId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
	) {
		return ResponseEntity.ok(issueQueryService.stats(projectId, from, to));
	}
}
