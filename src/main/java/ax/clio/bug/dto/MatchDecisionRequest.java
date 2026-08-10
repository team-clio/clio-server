package ax.clio.bug.dto;

import java.math.BigDecimal;
import java.util.List;

import ax.clio.bug.entity.BugMatchAction;
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
public record MatchDecisionRequest(
		@NotNull @Positive Long bugId,
		@NotNull BugMatchAction action,
		@Positive Long matchedIssueId,
		@NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
		List<String> supportingReasons,
		List<String> contradictions,
		List<String> reviewReasons,
		@Valid @Size(max = 5) List<CandidateComparisonRequest> candidateComparisons
) {
	public MatchDecisionRequest {
		supportingReasons = supportingReasons == null ? List.of() : List.copyOf(supportingReasons);
		contradictions = contradictions == null ? List.of() : List.copyOf(contradictions);
		reviewReasons = reviewReasons == null ? List.of() : List.copyOf(reviewReasons);
		candidateComparisons = candidateComparisons == null ? List.of() : List.copyOf(candidateComparisons);
	}

	@JsonIgnore
	@AssertTrue(message = "matchedIssueId is required for AUTO_LINK and REVIEW, and forbidden for CREATE_NEW.")
	public boolean isMatchedIssueContractValid() {
		return action == null
				|| (action == BugMatchAction.CREATE_NEW) == (matchedIssueId == null);
	}
}
