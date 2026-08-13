package ax.clio.analysis.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SaveAnalysisResultRequest(
		@NotNull @Positive Long issueId,
		@Positive Long previousAnalysisResultId,
		@NotNull JsonNode issueAnalysis
) {
	@JsonIgnore
	@AssertTrue(message = "issue_analysis must be a JSON object")
	public boolean isIssueAnalysisObject() {
		return issueAnalysis == null || issueAnalysis.isObject();
	}
}
