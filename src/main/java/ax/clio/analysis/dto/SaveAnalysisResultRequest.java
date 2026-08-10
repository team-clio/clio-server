package ax.clio.analysis.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SaveAnalysisResultRequest(
		@NotBlank @Size(max = 255) String requestId,
		@NotNull JsonNode issueAnalysis
) {
	@JsonIgnore
	@AssertTrue(message = "issue_analysis must be a JSON object")
	public boolean isIssueAnalysisObject() {
		return issueAnalysis == null || issueAnalysis.isObject();
	}
}
