package ax.clio.project.dto;

import java.time.Instant;

import ax.clio.project.entity.ProjectContext;

public record ProjectDocumentResponse(
		Long id,
		Long projectId,
		String title,
		String originalFilename,
		String mediaType,
		Instant createdAt
) {
	public static ProjectDocumentResponse from(ProjectContext document) {
		return new ProjectDocumentResponse(
				document.getId(), document.getProject().getId(), document.getTitle(),
				document.getOriginalFilename(), document.getMediaType(), document.getCreatedAt()
		);
	}
}
