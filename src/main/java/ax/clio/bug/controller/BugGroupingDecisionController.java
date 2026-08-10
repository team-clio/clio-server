package ax.clio.bug.controller;

import ax.clio.bug.dto.ApplyBugGroupingDecisionRequest;
import ax.clio.bug.dto.BugGroupingDecisionResponse;
import ax.clio.bug.entity.BugGroupingAction;
import ax.clio.bug.service.BugGroupingDecisionService;
import ax.clio.common.idempotency.AgentOperationType;
import ax.clio.common.idempotency.IdempotentOperationService;
import ax.clio.common.idempotency.IdempotentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/bug-reports/{reportId}/grouping-decisions")
@RequiredArgsConstructor
public class BugGroupingDecisionController {

	private static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

	private final IdempotentOperationService idempotentOperationService;
	private final BugGroupingDecisionService groupingDecisionService;

	@PostMapping
	public ResponseEntity<BugGroupingDecisionResponse> apply(
			@PathVariable Long projectId,
			@PathVariable Long reportId,
			@Valid @RequestBody ApplyBugGroupingDecisionRequest request
	) {
		int successStatus = request.groupingDecision().action() == BugGroupingAction.CREATE_NEW
				? HttpStatus.CREATED.value()
				: HttpStatus.OK.value();
		IdempotentResponse<BugGroupingDecisionResponse> result = idempotentOperationService.execute(
				projectId,
				AgentOperationType.BUG_GROUPING_DECISION,
				request.requestId(),
				new GroupingOperationRequest(reportId, request),
				BugGroupingDecisionResponse.class,
				successStatus,
				() -> groupingDecisionService.apply(projectId, reportId, request)
		);
		return ResponseEntity.status(result.httpStatus())
				.header(IDEMPOTENT_REPLAYED, Boolean.toString(result.replayed()))
				.body(result.body());
	}

	private record GroupingOperationRequest(Long reportId, Object body) {
	}
}
