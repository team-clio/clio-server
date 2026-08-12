package ax.clio.project.service;

import java.util.List;

import ax.clio.common.ConflictException;
import ax.clio.project.dto.CreateProjectRequest;
import ax.clio.project.dto.ProjectResponse;
import ax.clio.project.entity.Project;
import ax.clio.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

	private final ProjectRepository projectRepository;

	public List<ProjectResponse> getProjects() {
		return projectRepository.findAllByOrderByNameAsc().stream()
				.map(ProjectResponse::from)
				.toList();
	}

	@Transactional
	public ProjectResponse createProject(CreateProjectRequest request) {
		if (projectRepository.existsByNormalizedName(Project.normalizeName(request.name()))) {
			throw new ConflictException("Project name already exists.");
		}
		Project project = Project.create(request.name(), request.description());
		try {
			return ProjectResponse.from(projectRepository.saveAndFlush(project));
		} catch (DataIntegrityViolationException exception) {
			throw new ConflictException("Project name already exists.");
		}
	}
}
