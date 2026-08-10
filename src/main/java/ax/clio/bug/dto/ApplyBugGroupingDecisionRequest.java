package ax.clio.bug.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ApplyBugGroupingDecisionRequest(
		@NotBlank @Size(max = 255) String requestId,
		@NotNull @Valid BugGroupingDecisionRequest groupingDecision
) {
}
