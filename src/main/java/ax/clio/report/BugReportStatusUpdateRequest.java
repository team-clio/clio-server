package ax.clio.report;

import jakarta.validation.constraints.NotNull;

public record BugReportStatusUpdateRequest(
		@NotNull
		BugReportStatus status
) {
}
