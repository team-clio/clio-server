package ax.clio.analysis.dto;

import ax.clio.analysis.entity.AnalysisJobStatus;
import ax.clio.analysis.entity.AnalysisResult;
import ax.clio.analysis.entity.AnalysisResultStatus;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AnalysisResultResponse(
		Long analysisResultId,
		Long analysisJobId,
		AnalysisJobStatus jobStatus,
		AnalysisResultStatus resultStatus
) {
	public static AnalysisResultResponse from(AnalysisResult result) {
		return new AnalysisResultResponse(
				result.getId(),
				result.getJob().getId(),
				result.getJob().getStatus(),
				result.getStatus()
		);
	}
}
