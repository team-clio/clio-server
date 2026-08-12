package ax.clio.project.dto;

import java.time.Instant;

import ax.clio.project.entity.Project;

public record ProjectResponse(
		Long id,
		String name,
		String description,
		String status,
		Instant createdAt,
		Instant updatedAt
) {

	public static ProjectResponse from(Project project) {
		return new ProjectResponse(
				project.getId(),
				project.getName(),
				project.getDescription(),
				project.getStatus().name(),
				project.getCreatedAt(),
				project.getUpdatedAt()
		);
	}
}
