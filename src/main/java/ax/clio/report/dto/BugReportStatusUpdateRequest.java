package ax.clio.report.dto;

import ax.clio.report.entity.BugReportStatus;

import jakarta.validation.constraints.NotNull;

public record BugReportStatusUpdateRequest(
		@NotNull
		BugReportStatus status
) {
}
