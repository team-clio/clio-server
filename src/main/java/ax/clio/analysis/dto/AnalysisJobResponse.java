package ax.clio.analysis.dto;

import ax.clio.analysis.entity.AnalysisJob;
import ax.clio.analysis.entity.AnalysisJobStatus;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AnalysisJobResponse(
		Long analysisJobId,
		Long projectId,
		Long issueId,
		Long triggerBugId,
		Long previousAnalysisJobId,
		AnalysisJobStatus status
) {
	public static AnalysisJobResponse from(AnalysisJob job) {
		return new AnalysisJobResponse(
				job.getId(),
				job.getProject().getId(),
				job.getIssue().getId(),
				job.getBug().getId(),
				job.getPreviousAnalysisJob() == null ? null : job.getPreviousAnalysisJob().getId(),
				job.getStatus()
		);
	}
}
