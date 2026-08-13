package ax.clio.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CreateWorkflowRunRequest(
		@NotBlank @Size(max = 255) String requestId,
		@NotBlank @Size(max = 80) String requestType,
		@NotNull JsonNode requestPayload
) {
}
