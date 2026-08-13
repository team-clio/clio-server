package ax.clio.bug.dto;

import ax.clio.bug.entity.Bug;

public record BugLifecycleResponse(
		Long id,
		Long projectId,
		String status,
		String severity
) {
	public static BugLifecycleResponse from(Bug bug) {
		return new BugLifecycleResponse(
				bug.getId(),
				bug.getProject().getId(),
				bug.getStatus().name(),
				bug.getSeverity() == null ? null : bug.getSeverity().name()
		);
	}
}
