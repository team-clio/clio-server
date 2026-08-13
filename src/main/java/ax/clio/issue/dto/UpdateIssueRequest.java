package ax.clio.issue.dto;

import ax.clio.bug.entity.Priority;
import ax.clio.bug.entity.Severity;
import ax.clio.issue.entity.IssueStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public record UpdateIssueRequest(
		IssueStatus status,
		Priority priority,
		Severity severity,
		@Size(max = 100) String assigneeName
) {
	@JsonIgnore
	@AssertTrue(message = "At least one issue lifecycle field is required.")
	public boolean hasUpdate() {
		return status != null || priority != null || severity != null || assigneeName != null;
	}
}
