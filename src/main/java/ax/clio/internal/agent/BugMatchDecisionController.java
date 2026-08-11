package ax.clio.internal.agent;

import ax.clio.bug.dto.ApplyMatchDecisionRequest;
import ax.clio.bug.dto.MatchDecisionResponse;
import ax.clio.bug.entity.BugMatchAction;
import ax.clio.bug.service.BugMatchDecisionService;
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
@RequestMapping("/internal/api/v1/projects/{projectId}/bugs/{bugId}/match-decisions")
@RequiredArgsConstructor
public class BugMatchDecisionController {

	private static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

	private final IdempotentOperationService idempotentOperationService;
	private final BugMatchDecisionService matchDecisionService;

	@PostMapping
	public ResponseEntity<MatchDecisionResponse> apply(
			@PathVariable Long projectId,
			@PathVariable Long bugId,
			@Valid @RequestBody ApplyMatchDecisionRequest request
	) {
		int successStatus = request.matchDecision().action() == BugMatchAction.CREATE_NEW
				? HttpStatus.CREATED.value()
				: HttpStatus.OK.value();
		IdempotentResponse<MatchDecisionResponse> result = idempotentOperationService.execute(
				projectId,
				AgentOperationType.MATCH_DECISION,
				request.requestId(),
				request,
				MatchDecisionResponse.class,
				successStatus,
				() -> matchDecisionService.apply(projectId, bugId, request)
		);
		return ResponseEntity.status(result.httpStatus())
				.header(IDEMPOTENT_REPLAYED, Boolean.toString(result.replayed()))
				.body(result.body());
	}
}
