package ax.clio.memory.decision.dto;

import ax.clio.memory.decision.entity.DecisionMemory;

import java.time.Instant;

public record DecisionMemoryResponse(
		Long id,
		Long projectId,
		String projectName,
		String title,
		String body,
		Instant createdAt
) {

	public static DecisionMemoryResponse from(DecisionMemory decision) {
		return new DecisionMemoryResponse(
				decision.getId(),
				decision.getProject().getId(),
				decision.getProject().getName(),
				decision.getTitle(),
				decision.getBody(),
				decision.getCreatedAt()
		);
	}
}
