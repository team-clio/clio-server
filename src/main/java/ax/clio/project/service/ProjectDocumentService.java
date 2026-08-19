package ax.clio.project.service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import ax.clio.common.ConflictException;
import ax.clio.common.ResourceNotFoundException;
import ax.clio.project.dto.ProjectDocumentResponse;
import ax.clio.project.entity.Project;
import ax.clio.project.entity.ProjectContext;
import ax.clio.project.repository.ProjectContextRepository;
import ax.clio.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectDocumentService {

	private final ProjectRepository projectRepository;
	private final ProjectContextRepository projectContextRepository;
	private final ProjectDocumentContentExtractor contentExtractor;

	public List<ProjectDocumentResponse> getDocuments(Long projectId) {
		requireProject(projectId);
		return projectContextRepository.findAllByProjectIdOrderByCreatedAtDesc(projectId).stream()
				.map(ProjectDocumentResponse::from)
				.toList();
	}

	@Transactional
	public ProjectDocumentResponse createDocument(Long projectId, String title, MultipartFile file) {
		Project project = requireProject(projectId);
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("Document title must not be blank.");
		}
		if (file == null || file.isEmpty() || file.getOriginalFilename() == null) {
			throw new IllegalArgumentException("A non-empty document file is required.");
		}
		byte[] bytes = bytesOf(file);
		String contentHash = hashOf(bytes);
		if (projectContextRepository.findByProjectIdAndContentHash(projectId, contentHash).isPresent()) {
			throw new ConflictException("An identical document is already registered for this project.");
		}
		String originalFilename = file.getOriginalFilename();
		String markdown = contentExtractor.extract(originalFilename, bytes);
		ProjectContext document = ProjectContext.createDocument(
				project, title, markdown, originalFilename, mediaTypeOf(originalFilename), contentHash
		);
		return ProjectDocumentResponse.from(projectContextRepository.saveAndFlush(document));
	}

	@Transactional
	public void deleteDocument(Long projectId, Long documentId) {
		requireProject(projectId);
		projectContextRepository.delete(requireDocument(projectId, documentId));
	}

	private Project requireProject(Long projectId) {
		return projectRepository.findById(projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
	}

	private ProjectContext requireDocument(Long projectId, Long documentId) {
		return projectContextRepository.findByIdAndProjectId(documentId, projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Project document not found: " + documentId));
	}

	private byte[] bytesOf(MultipartFile file) {
		try {
			return file.getBytes();
		} catch (IOException exception) {
			throw new IllegalArgumentException("Document file could not be read.", exception);
		}
	}

	private String hashOf(byte[] bytes) {
		try {
			return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	private String mediaTypeOf(String filename) {
		String normalized = filename.toLowerCase(java.util.Locale.ROOT);
		return normalized.endsWith(".pdf") ? "application/pdf" : "text/markdown";
	}
}
