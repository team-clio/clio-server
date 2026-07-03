package ax.clio.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BugReportCreateRequest(
		@NotNull
		Long projectId,

		@NotBlank
		@Size(max = 200)
		String title,

		@NotBlank
		@Size(max = 10000)
		String description
) {
}
