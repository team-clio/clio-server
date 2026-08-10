package ax.clio.analysis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CreateAnalysisJobRequest(
		@NotBlank @Size(max = 255) String requestId,
		@NotNull @Positive Long triggerBugId,
		@Positive Long previousAnalysisJobId
) {
}
