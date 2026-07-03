package ax.clio.project;

import java.util.List;

import ax.clio.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

	private final ProjectService projectService;

	public ProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@PostMapping
	public ApiResponse<ProjectResponse> create(@Valid @RequestBody ProjectCreateRequest request) {
		return ApiResponse.ok(projectService.create(request));
	}

	@GetMapping
	public ApiResponse<List<ProjectResponse>> findAll() {
		return ApiResponse.ok(projectService.findAll());
	}

	@GetMapping("/{id}")
	public ApiResponse<ProjectResponse> findById(@PathVariable Long id) {
		return ApiResponse.ok(projectService.findById(id));
	}

	@DeleteMapping("/{id}")
	public ApiResponse<Void> delete(@PathVariable Long id) {
		projectService.delete(id);
		return ApiResponse.ok();
	}
}
