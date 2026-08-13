package ax.clio.issue.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record IssueBugLifecycleResponse(
		Long issueId,
		Long bugId,
		boolean issueCreated,
		boolean bugLinked
) {
}
