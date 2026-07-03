package ax.clio.project;

import java.time.Instant;

public record ProjectResponse(
		Long id,
		String name,
		String rootPath,
		String description,
		Instant createdAt
) {

	public static ProjectResponse from(Project project) {
		return new ProjectResponse(
				project.getId(),
				project.getName(),
				project.getRootPath(),
				project.getDescription(),
				project.getCreatedAt()
		);
	}
}
