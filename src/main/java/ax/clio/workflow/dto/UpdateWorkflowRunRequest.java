package ax.clio.workflow.dto;

import ax.clio.workflow.entity.AgentWorkflowStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UpdateWorkflowRunRequest(
		@NotNull AgentWorkflowStatus status,
		JsonNode latestCheckpoint,
		JsonNode resultSnapshot,
		@Size(max = 100) String failureCode,
		@Size(max = 2000) String failureMessage
) {
}
