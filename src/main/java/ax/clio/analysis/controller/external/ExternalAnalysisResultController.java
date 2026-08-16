package ax.clio.analysis.controller.external;

import static ax.clio.common.api.ApiPaths.EXTERNAL_V1;

import ax.clio.analysis.dto.LatestIssueAnalysisResponse;
import ax.clio.analysis.service.AnalysisResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(EXTERNAL_V1 + "/projects/{projectId}/issues")
@RequiredArgsConstructor
public class ExternalAnalysisResultController {

	private final AnalysisResultService analysisResultService;

	@GetMapping("/{issueId}/analysis-results/latest")
	public ResponseEntity<LatestIssueAnalysisResponse> latest(
			@PathVariable Long projectId,
			@PathVariable Long issueId
	) {
		return ResponseEntity.ok(analysisResultService.latestForClient(projectId, issueId));
	}

	@GetMapping("/{issueId}/analysis-results/latest/code-evidence")
	public ResponseEntity<java.util.Map<String, Object>> codeEvidence(
			@PathVariable Long projectId,
			@PathVariable Long issueId
	) {
		return ResponseEntity.ok(analysisResultService.codeEvidence(projectId, issueId));
	}
}
