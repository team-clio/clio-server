package ax.clio.analysis.dto;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record LatestAnalysisResultResponse(
		Long analysisResultId,
		Long workflowRunId,
		JsonNode issueAnalysis
) {
}
