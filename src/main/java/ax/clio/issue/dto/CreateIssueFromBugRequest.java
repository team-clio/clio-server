package ax.clio.issue.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CreateIssueFromBugRequest(
		@NotNull @Positive Long workflowRunId,
		@NotNull @Positive Long bugId,
		@NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
		@Size(max = 200) String title,
		String description
) {
}
