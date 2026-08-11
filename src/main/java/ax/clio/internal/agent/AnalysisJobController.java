package ax.clio.internal.agent;

import ax.clio.analysis.dto.AnalysisJobContextResponse;
import ax.clio.analysis.dto.AnalysisJobResponse;
import ax.clio.analysis.dto.AnalysisResultResponse;
import ax.clio.analysis.dto.CreateAnalysisJobRequest;
import ax.clio.analysis.dto.SaveAnalysisResultRequest;
import ax.clio.analysis.dto.UpdateAnalysisJobRequest;
import ax.clio.analysis.service.AnalysisJobService;
import ax.clio.common.idempotency.AgentOperationType;
import ax.clio.common.idempotency.IdempotentOperationService;
import ax.clio.common.idempotency.IdempotentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/api/v1/projects/{projectId}")
@RequiredArgsConstructor
public class AnalysisJobController {

	private static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

	private final IdempotentOperationService idempotentOperationService;
	private final AnalysisJobService analysisJobService;

	@PostMapping("/issues/{issueId}/analysis-jobs")
	public ResponseEntity<AnalysisJobResponse> create(
			@PathVariable Long projectId,
			@PathVariable Long issueId,
			@Valid @RequestBody CreateAnalysisJobRequest request
	) {
		IdempotentResponse<AnalysisJobResponse> result = idempotentOperationService.execute(
				projectId,
				AgentOperationType.ANALYSIS_JOB_CREATE,
				request.requestId(),
				new IssueOperationRequest(issueId, request),
				AnalysisJobResponse.class,
				HttpStatus.CREATED.value(),
				() -> analysisJobService.create(projectId, issueId, request)
		);
		return response(result);
	}

	@GetMapping("/analysis-jobs/{jobId}/context")
	public AnalysisJobContextResponse getContext(
			@PathVariable Long projectId,
			@PathVariable Long jobId
	) {
		return analysisJobService.getContext(projectId, jobId);
	}

	@PatchMapping("/analysis-jobs/{jobId}")
	public ResponseEntity<AnalysisJobResponse> update(
			@PathVariable Long projectId,
			@PathVariable Long jobId,
			@Valid @RequestBody UpdateAnalysisJobRequest request
	) {
		IdempotentResponse<AnalysisJobResponse> result = idempotentOperationService.execute(
				projectId,
				AgentOperationType.ANALYSIS_JOB_STATUS,
				request.requestId(),
				new JobOperationRequest(jobId, request),
				AnalysisJobResponse.class,
				HttpStatus.OK.value(),
				() -> analysisJobService.update(projectId, jobId, request)
		);
		return response(result);
	}

	@PutMapping("/analysis-jobs/{jobId}/result")
	public ResponseEntity<AnalysisResultResponse> saveResult(
			@PathVariable Long projectId,
			@PathVariable Long jobId,
			@Valid @RequestBody SaveAnalysisResultRequest request
	) {
		IdempotentResponse<AnalysisResultResponse> result = idempotentOperationService.execute(
				projectId,
				AgentOperationType.ANALYSIS_RESULT,
				request.requestId(),
				new JobOperationRequest(jobId, request),
				AnalysisResultResponse.class,
				HttpStatus.CREATED.value(),
				() -> analysisJobService.saveResult(projectId, jobId, request)
		);
		return response(result);
	}

	private <T> ResponseEntity<T> response(IdempotentResponse<T> result) {
		return ResponseEntity.status(result.httpStatus())
				.header(IDEMPOTENT_REPLAYED, Boolean.toString(result.replayed()))
				.body(result.body());
	}

	private record IssueOperationRequest(Long issueId, Object body) {
	}

	private record JobOperationRequest(Long jobId, Object body) {
	}
}
