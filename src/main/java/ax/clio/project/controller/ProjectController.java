package ax.clio.project.controller;

import ax.clio.common.dto.ListResponse;
import ax.clio.project.dto.CreateProjectRequest;
import ax.clio.project.dto.ProjectResponse;
import ax.clio.project.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
}
