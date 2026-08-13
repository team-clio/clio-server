package ax.clio.bug.controller.external;

import static ax.clio.common.api.ApiPaths.EXTERNAL_V1;

import java.time.Instant;

import ax.clio.bug.dto.BugCollectRequest;
import ax.clio.bug.dto.BugLifecycleResponse;
import ax.clio.bug.dto.BugResponse;
import ax.clio.bug.dto.BugSummaryResponse;
import ax.clio.bug.dto.UpdateBugRequest;
import ax.clio.bug.entity.BugSource;
import ax.clio.bug.entity.BugStatus;
import ax.clio.bug.entity.Severity;
import ax.clio.bug.service.BugService;
import ax.clio.bug.service.BugLifecycleService;
import ax.clio.common.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(EXTERNAL_V1 + "/projects/{projectId}/bugs")
@RequiredArgsConstructor
@Validated
public class ExternalBugController {

	private final BugService bugService;
	private final BugLifecycleService bugLifecycleService;

	@PostMapping
	public ResponseEntity<BugResponse> collect(
			@PathVariable Long projectId,
			@Valid @RequestBody BugCollectRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(bugService.collect(projectId, request));
	}

	@GetMapping
	public PageResponse<BugSummaryResponse> search(
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
		return bugService.search(projectId, from, to, issueId, source, status, severity, page, size);
	}

	@PatchMapping("/{bugId}")
	public ResponseEntity<BugLifecycleResponse> updateBug(
			@PathVariable Long projectId,
			@PathVariable Long bugId,
			@Valid @RequestBody UpdateBugRequest request
	) {
		return ResponseEntity.ok(bugLifecycleService.update(projectId, bugId, request));
	}
}
