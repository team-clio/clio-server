package ax.clio.issue.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CandidateBugLinksRequest(
		@NotEmpty @Size(max = 100) List<@Positive Long> bugIds
) {
	public CandidateBugLinksRequest {
		bugIds = bugIds == null ? List.of() : List.copyOf(bugIds);
	}
}
