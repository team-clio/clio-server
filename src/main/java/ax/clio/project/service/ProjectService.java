package ax.clio.project.service;

import java.util.List;

import ax.clio.project.dto.CreateProjectRequest;
import ax.clio.project.dto.ProjectResponse;
import ax.clio.project.entity.Project;
import ax.clio.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

	private final ProjectRepository projectRepository;

	public List<ProjectResponse> getProjects() {
		return projectRepository.findAll().stream()
				.map(ProjectResponse::from)
				.toList();
	}

	@Transactional
	public ProjectResponse createProject(CreateProjectRequest request) {
		Project project = Project.create(request.name(), request.description());
		return ProjectResponse.from(projectRepository.save(project));
	}
}
