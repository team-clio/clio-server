package ax.clio.analysis.dto;

import ax.clio.analysis.entity.AnalysisJobStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UpdateAnalysisJobRequest(
		@NotBlank @Size(max = 255) String requestId,
		@NotNull AnalysisJobStatus status,
		@Size(max = 1000) String failureReason
) {
	@JsonIgnore
	@AssertTrue(message = "status must be RUNNING or FAILED, with failure_reason only for FAILED")
	public boolean isValidTransitionRequest() {
		if (status == null) {
			return true;
		}
		return switch (status) {
			case RUNNING -> failureReason == null || failureReason.isBlank();
			case FAILED -> failureReason != null && !failureReason.isBlank();
			default -> false;
		};
	}
}
