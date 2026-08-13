package ax.clio.issue.dto;

import java.time.Instant;

public record IssueSummaryResponse(
		Long id,
		Long projectId,
		String title,
		String summary,
		String status,
		String priority,
		String severity,
		String assigneeName,
		Double aiConfidence,
		Integer bugCount,
		Integer importanceScore,
		Integer riskScore,
		Instant firstSeenAt,
		Instant lastSeenAt,
		Instant updatedAt
) {
	public static IssueSummaryResponse from(ax.clio.issue.entity.Issue issue) {
		return new IssueSummaryResponse(
				issue.getId(),
				issue.getProject().getId(),
				issue.getTitle(),
				issue.getSummary(),
				issue.getStatus().name(),
				issue.getPriority() == null ? null : issue.getPriority().name(),
				issue.getSeverity() == null ? null : issue.getSeverity().name(),
				issue.getAssigneeName(),
				issue.getAiConfidence() == null ? null : issue.getAiConfidence().doubleValue(),
				issue.getBugCount(),
				issue.getImportanceScore(),
				issue.getRiskScore(),
				issue.getFirstSeenAt(),
				issue.getLastSeenAt(),
				issue.getUpdatedAt()
		);
	}
}
