package ax.clio.project;

import java.util.List;

import ax.clio.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

	private final ProjectRepository projectRepository;
	private final ProjectPathValidator projectPathValidator;

	public ProjectService(ProjectRepository projectRepository, ProjectPathValidator projectPathValidator) {
		this.projectRepository = projectRepository;
		this.projectPathValidator = projectPathValidator;
	}

	@Transactional
	public ProjectResponse create(ProjectCreateRequest request) {
		String rootPath = projectPathValidator.validate(request.rootPath());
		Project project = new Project(request.name(), rootPath, request.description());
		return ProjectResponse.from(projectRepository.save(project));
	}

	@Transactional(readOnly = true)
	public List<ProjectResponse> findAll() {
		return projectRepository.findAll().stream()
				.map(ProjectResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public ProjectResponse findById(Long id) {
		return ProjectResponse.from(getProject(id));
	}

	@Transactional
	public void delete(Long id) {
		if (!projectRepository.existsById(id)) {
			throw new BusinessException(HttpStatus.NOT_FOUND, "Project not found");
		}
		projectRepository.deleteById(id);
	}

	public Project getProject(Long id) {
		return projectRepository.findById(id)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Project not found"));
	}
}
