package ax.clio.analysis.controller.internal;

import static ax.clio.common.api.ApiPaths.INTERNAL_V1;

import ax.clio.analysis.dto.AnalysisResultResponse;
import ax.clio.analysis.dto.LatestAnalysisResultResponse;
import ax.clio.analysis.dto.SaveAnalysisResultRequest;
import ax.clio.analysis.service.AnalysisResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(INTERNAL_V1 + "/projects/{projectId}")
@RequiredArgsConstructor
public class InternalAnalysisResultController {
	private final AnalysisResultService analysisResultService;

	@PutMapping("/workflow-runs/{runId}/analysis-result")
	public ResponseEntity<AnalysisResultResponse> save(
			@PathVariable Long projectId,
			@PathVariable Long runId,
			@Valid @RequestBody SaveAnalysisResultRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(analysisResultService.save(projectId, runId, request));
	}

	@GetMapping("/issues/{issueId}/analysis-results/latest")
	public LatestAnalysisResultResponse latest(@PathVariable Long projectId, @PathVariable Long issueId) {
		return analysisResultService.latest(projectId, issueId);
	}
}
