package ax.clio.bug.dto;

import ax.clio.bug.entity.BugMatchAction;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MatchDecisionResponse(
		Long decisionId,
		Long bugId,
		Long bugReportId,
		BugMatchAction action,
		Long matchedIssueId,
		Long resultingIssueId,
		boolean linked
) {
}
