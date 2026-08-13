package ax.clio.workflow.dto;

import java.time.Instant;

import ax.clio.workflow.entity.AgentWorkflowStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record WorkflowRunResponse(
		Long id,
		Long projectId,
		String requestId,
		String requestType,
		AgentWorkflowStatus status,
		JsonNode latestCheckpoint,
		JsonNode resultSnapshot,
		String failureCode,
		String failureMessage,
		Instant startedAt,
		Instant completedAt,
		Instant createdAt,
		Instant updatedAt
) {
}
