package ax.clio.bug.dto;

import java.math.BigDecimal;
import java.util.List;

import ax.clio.bug.entity.BugGroupingAction;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BugGroupingDecisionRequest(
		@NotNull @Positive Long bugReportId,
		@NotNull BugGroupingAction action,
		@Positive Long matchedBugId,
		@NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
		List<String> supportingReasons,
		List<String> contradictions,
		List<String> reviewReasons,
		@Valid @Size(max = 5) List<BugCandidateComparisonRequest> candidateComparisons
) {
	public BugGroupingDecisionRequest {
		supportingReasons = supportingReasons == null ? List.of() : List.copyOf(supportingReasons);
		contradictions = contradictions == null ? List.of() : List.copyOf(contradictions);
		reviewReasons = reviewReasons == null ? List.of() : List.copyOf(reviewReasons);
		candidateComparisons = candidateComparisons == null ? List.of() : List.copyOf(candidateComparisons);
	}

	@JsonIgnore
	@AssertTrue(message = "matchedBugId is required only for MATCH_EXISTING and optional for REVIEW.")
	public boolean isMatchedBugContractValid() {
		if (action == null) {
			return true;
		}
		return switch (action) {
			case MATCH_EXISTING -> matchedBugId != null;
			case CREATE_NEW -> matchedBugId == null;
			case REVIEW -> true;
		};
	}
}
