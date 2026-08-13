package ax.clio.workflow.controller.internal;

import static ax.clio.common.api.ApiPaths.INTERNAL_V1;

import ax.clio.workflow.dto.CreateWorkflowRunRequest;
import ax.clio.workflow.dto.UpdateWorkflowRunRequest;
import ax.clio.workflow.dto.WorkflowRunResponse;
import ax.clio.workflow.service.AgentWorkflowRunService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(INTERNAL_V1 + "/projects/{projectId}/workflow-runs")
@RequiredArgsConstructor
public class InternalAgentWorkflowRunController {
	private final AgentWorkflowRunService workflowRunService;

	@PostMapping
	public ResponseEntity<WorkflowRunResponse> create(
			@PathVariable Long projectId,
			@Valid @RequestBody CreateWorkflowRunRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(workflowRunService.createPending(projectId, request));
	}

	@GetMapping("/{runId}")
	public WorkflowRunResponse get(@PathVariable Long projectId, @PathVariable Long runId) {
		return workflowRunService.get(projectId, runId);
	}

	@PatchMapping("/{runId}")
	public WorkflowRunResponse update(
			@PathVariable Long projectId,
			@PathVariable Long runId,
			@Valid @RequestBody UpdateWorkflowRunRequest request
	) {
		return workflowRunService.update(projectId, runId, request);
	}
}
