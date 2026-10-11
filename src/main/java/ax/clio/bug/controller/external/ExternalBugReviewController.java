package ax.clio.bug.controller.external;

import static ax.clio.common.api.ApiPaths.EXTERNAL_V1;

import java.util.List;

import ax.clio.bug.dto.BugReviewResponse;
import ax.clio.bug.dto.LinkReviewedBugRequest;
import ax.clio.bug.service.BugReviewService;
import ax.clio.issue.dto.IssueBugLifecycleResponse;
import ax.clio.issue.service.IssueLifecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매칭이 사람의 판단을 요청한 버그의 검토 대기열.
 */
@RestController
@RequestMapping(EXTERNAL_V1 + "/projects/{projectId}/bug-reviews")
@RequiredArgsConstructor
public class ExternalBugReviewController {

	private final BugReviewService bugReviewService;
	private final IssueLifecycleService issueLifecycleService;

	@GetMapping
	public List<BugReviewResponse> list(@PathVariable Long projectId) {
		return bugReviewService.list(projectId);
	}

	@PostMapping("/{bugId}/link")
	public IssueBugLifecycleResponse link(
			@PathVariable Long projectId,
			@PathVariable Long bugId,
			@Valid @RequestBody LinkReviewedBugRequest request
	) {
		return issueLifecycleService.linkReviewedBug(projectId, bugId, request.issueId());
	}
}
