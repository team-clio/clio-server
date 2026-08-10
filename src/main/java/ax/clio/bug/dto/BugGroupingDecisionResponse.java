package ax.clio.bug.dto;

import ax.clio.bug.entity.BugGroupingAction;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BugGroupingDecisionResponse(
		Long groupingDecisionId,
		Long bugReportId,
		BugGroupingAction action,
		Long matchedBugId,
		Long resultingBugId,
		Long resultingIssueId,
		boolean readyForIssueMatching
) {
}
