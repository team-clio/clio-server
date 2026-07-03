package ax.clio.report;

import java.time.Instant;

public record BugReportResponse(
		Long id,
		Long projectId,
		String projectName,
		String title,
		String description,
		BugReportStatus status,
		Instant createdAt
) {

	public static BugReportResponse from(BugReport report) {
		return new BugReportResponse(
				report.getId(),
				report.getProject().getId(),
				report.getProject().getName(),
				report.getTitle(),
				report.getDescription(),
				report.getStatus(),
				report.getCreatedAt()
		);
	}
}
