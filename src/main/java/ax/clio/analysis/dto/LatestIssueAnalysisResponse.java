package ax.clio.analysis.dto;

import tools.jackson.databind.JsonNode;

public record LatestIssueAnalysisResponse(
		Long analysisResultId,
		Long workflowRunId,
		JsonNode issueAnalysis
) {
}
