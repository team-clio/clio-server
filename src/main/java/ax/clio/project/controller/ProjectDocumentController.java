package ax.clio.project.controller;

import ax.clio.common.dto.ListResponse;
import ax.clio.project.dto.ProjectDocumentResponse;
import ax.clio.project.service.ProjectDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/documents")
@RequiredArgsConstructor
public class ProjectDocumentController {

	private final ProjectDocumentService projectDocumentService;

	@GetMapping
	public ResponseEntity<ListResponse<ProjectDocumentResponse>> getDocuments(@PathVariable Long projectId) {
		return ResponseEntity.ok(new ListResponse<>(projectDocumentService.getDocuments(projectId)));
	}

	@PostMapping(consumes = "multipart/form-data")
	public ResponseEntity<ProjectDocumentResponse> createDocument(
			@PathVariable Long projectId,
			@RequestParam String title,
			@RequestParam MultipartFile file
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(projectDocumentService.createDocument(projectId, title, file));
	}

	@DeleteMapping("/{documentId}")
	public ResponseEntity<Void> deleteDocument(@PathVariable Long projectId, @PathVariable Long documentId) {
		projectDocumentService.deleteDocument(projectId, documentId);
		return ResponseEntity.noContent().build();
	}
}
