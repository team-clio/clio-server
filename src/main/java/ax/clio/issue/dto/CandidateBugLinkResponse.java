package ax.clio.issue.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CandidateBugLinkResponse(
		Long bugId,
		Long issueId,
		String issueTitle,
		String issueStatus,
		String issueSummary
) {
}
