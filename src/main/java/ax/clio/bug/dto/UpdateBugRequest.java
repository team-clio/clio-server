package ax.clio.bug.dto;

import ax.clio.bug.entity.BugStatus;
import ax.clio.bug.entity.Severity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;

public record UpdateBugRequest(BugStatus status, Severity severity) {

	@JsonIgnore
	@AssertTrue(message = "At least one bug lifecycle field is required.")
	public boolean hasUpdate() {
		return status != null || severity != null;
	}
}
