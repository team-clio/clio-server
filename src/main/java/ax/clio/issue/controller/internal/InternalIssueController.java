package ax.clio.issue.controller.internal;

import static ax.clio.common.api.ApiPaths.INTERNAL_V1;

import java.util.List;

import ax.clio.issue.dto.CandidateBugLinkResponse;
import ax.clio.issue.dto.CandidateBugLinksRequest;
import ax.clio.issue.dto.CreateIssueFromBugRequest;
import ax.clio.issue.dto.IssueBugLifecycleResponse;
import ax.clio.issue.dto.IssueDetailResponse;
import ax.clio.issue.dto.LinkBugToIssueRequest;
import ax.clio.issue.service.InternalIssueQueryService;
import ax.clio.issue.service.IssueLifecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(INTERNAL_V1 + "/projects/{projectId}")
@RequiredArgsConstructor
public class InternalIssueController {
	private final InternalIssueQueryService issueQueryService;
	private final IssueLifecycleService issueLifecycleService;

	@PostMapping("/issues")
	public ResponseEntity<IssueBugLifecycleResponse> create(
			@PathVariable Long projectId,
			@Valid @RequestBody CreateIssueFromBugRequest request
	) {
		IssueBugLifecycleResponse response = issueLifecycleService.createFromBug(projectId, request);
		return ResponseEntity.status(response.issueCreated() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(response);
	}

	@PostMapping("/issues/{issueId}/bugs")
	public ResponseEntity<IssueBugLifecycleResponse> linkBug(
			@PathVariable Long projectId,
			@PathVariable Long issueId,
			@Valid @RequestBody LinkBugToIssueRequest request
	) {
		IssueBugLifecycleResponse response = issueLifecycleService.linkBug(projectId, issueId, request);
		return ResponseEntity.status(response.bugLinked() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(response);
	}

	@GetMapping("/issues/{issueId}")
	public IssueDetailResponse get(@PathVariable Long projectId, @PathVariable Long issueId) {
		return issueQueryService.get(projectId, issueId);
	}

	@PostMapping("/candidate-bug-links")
	public List<CandidateBugLinkResponse> candidateLinks(
			@PathVariable Long projectId,
			@Valid @RequestBody CandidateBugLinksRequest request
	) {
		return issueQueryService.links(projectId, request);
	}
}
