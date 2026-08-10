package ax.clio.bug.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BugCandidateComparisonRequest(
		@NotNull @Positive Long bugId,
		@NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
		List<String> supportingReasons,
		List<String> contradictions,
		List<String> missingInformation
) {
	public BugCandidateComparisonRequest {
		supportingReasons = supportingReasons == null ? List.of() : List.copyOf(supportingReasons);
		contradictions = contradictions == null ? List.of() : List.copyOf(contradictions);
		missingInformation = missingInformation == null ? List.of() : List.copyOf(missingInformation);
	}
}
