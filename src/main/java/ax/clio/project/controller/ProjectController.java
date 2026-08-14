package ax.clio.project.controller;

import ax.clio.common.dto.ListResponse;
import ax.clio.project.dto.CreateProjectRequest;
import ax.clio.project.dto.ProjectResponse;
import ax.clio.project.dto.RepositoryRequest;
import ax.clio.project.dto.RepositoryResponse;
import ax.clio.project.dto.UpdateProjectRequest;
import ax.clio.project.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

	private final ProjectService projectService;

	@GetMapping
	public ResponseEntity<ListResponse<ProjectResponse>> getProjects() {
		return ResponseEntity.ok(new ListResponse<>(projectService.getProjects()));
	}

	@PostMapping
	public ResponseEntity<ProjectResponse> createProject(
			@Valid @RequestBody CreateProjectRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(projectService.createProject(request));
	}

	@PatchMapping("/{projectId}")
	public ResponseEntity<ProjectResponse> updateProject(
			@PathVariable Long projectId,
			@Valid @RequestBody UpdateProjectRequest request
	) {
		return ResponseEntity.ok(projectService.updateProject(projectId, request));
	}

	@GetMapping("/{projectId}/repositories")
	public ResponseEntity<ListResponse<RepositoryResponse>> getRepositories(@PathVariable Long projectId) {
		return ResponseEntity.ok(new ListResponse<>(projectService.getRepositories(projectId)));
	}

	@PostMapping("/{projectId}/repositories")
	public ResponseEntity<RepositoryResponse> createRepository(
			@PathVariable Long projectId,
			@Valid @RequestBody RepositoryRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(projectService.createRepository(projectId, request));
	}

	@PatchMapping("/{projectId}/repositories/{repositoryId}")
	public ResponseEntity<RepositoryResponse> updateRepository(
			@PathVariable Long projectId,
			@PathVariable Long repositoryId,
			@Valid @RequestBody RepositoryRequest request
	) {
		return ResponseEntity.ok(projectService.updateRepository(projectId, repositoryId, request));
	}

	@DeleteMapping("/{projectId}/repositories/{repositoryId}")
	public ResponseEntity<Void> deleteRepository(
			@PathVariable Long projectId,
			@PathVariable Long repositoryId
	) {
		projectService.deleteRepository(projectId, repositoryId);
		return ResponseEntity.noContent().build();
	}
}
