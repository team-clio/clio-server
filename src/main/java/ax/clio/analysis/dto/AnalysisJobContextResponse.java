package ax.clio.analysis.dto;

import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AnalysisJobContextResponse(
		Long analysisJobId,
		Long projectId,
		IssueContext issue,
		Long triggerBugId,
		List<BugContext> bugs,
		JsonNode previousAnalysis
) {
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record IssueContext(Long issueId, String title, String summary) {
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record BugContext(Long bugId, Long latestBugReportId, int occurrenceCount) {
	}
}
