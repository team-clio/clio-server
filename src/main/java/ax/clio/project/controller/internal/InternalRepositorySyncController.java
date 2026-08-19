package ax.clio.project.controller.internal;

import static ax.clio.common.api.ApiPaths.INTERNAL_V1;

import ax.clio.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(INTERNAL_V1 + "/projects/{projectId}/repositories/{repositoryId}/sync")
@RequiredArgsConstructor
public class InternalRepositorySyncController {
	private final ProjectService projectService;

	@PatchMapping("/completed")
	public ResponseEntity<Void> completed(@PathVariable Long projectId, @PathVariable Long repositoryId) {
		projectService.markRepositorySynced(projectId, repositoryId);
		return ResponseEntity.noContent().build();
	}

	@PatchMapping("/failed")
	public ResponseEntity<Void> failed(@PathVariable Long projectId, @PathVariable Long repositoryId) {
		projectService.markRepositorySyncFailed(projectId, repositoryId);
		return ResponseEntity.noContent().build();
	}
}
