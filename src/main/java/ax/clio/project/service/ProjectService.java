package ax.clio.project.service;

import java.util.List;

import ax.clio.agent.client.RepositorySyncRequestType;
import ax.clio.agent.event.RepositorySyncEvent;
import ax.clio.agent.event.RepositorySyncCompletedEvent;
import ax.clio.common.ConflictException;
import ax.clio.common.ResourceNotFoundException;
import ax.clio.project.dto.CreateProjectRequest;
import ax.clio.project.dto.ProjectResponse;
import ax.clio.project.dto.RepositoryRequest;
import ax.clio.project.dto.RepositoryResponse;
import ax.clio.project.dto.UpdateProjectRequest;
import ax.clio.project.entity.Project;
import ax.clio.project.entity.ProjectSource;
import ax.clio.project.repository.ProjectRepository;
import ax.clio.project.repository.ProjectSourceRepository;
import ax.clio.project.repository.RepositoryCredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

	private final ProjectRepository projectRepository;
	private final ProjectSourceRepository projectSourceRepository;
	private final RepositoryCredentialRepository repositoryCredentialRepository;
	private final ApplicationEventPublisher eventPublisher;

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

	@Transactional
	public ProjectResponse updateProject(Long projectId, UpdateProjectRequest request) {
		Project project = projectRepository.findByIdForUpdate(projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
		if (projectRepository.existsByNormalizedNameAndIdNot(Project.normalizeName(request.name()), projectId)) {
			throw new ConflictException("Project name already exists.");
		}
		project.update(request.name(), request.description());
		try {
			return ProjectResponse.from(projectRepository.saveAndFlush(project));
		} catch (DataIntegrityViolationException exception) {
			throw new ConflictException("Project name already exists.");
		}
	}

	public List<RepositoryResponse> getRepositories(Long projectId) {
		requireProject(projectId);
		return projectSourceRepository.findAllByProjectIdOrderByCreatedAtAsc(projectId).stream()
				.map(RepositoryResponse::from)
				.toList();
	}

	@Transactional
	public RepositoryResponse createRepository(Long projectId, RepositoryRequest request) {
		Project project = requireProject(projectId);
		String repoUrl = request.url().trim();
		if (projectSourceRepository.existsByProjectIdAndRepoUrl(projectId, repoUrl)) {
			throw new ConflictException("Repository is already connected to this project.");
		}
		ProjectSource source = ProjectSource.create(
				project, request.provider(), request.owner(), request.name(), repoUrl,
				request.defaultBranch(), joinPaths(request.includePaths()), joinPaths(request.excludePaths()), request.enabled()
		);
		try {
			source = projectSourceRepository.saveAndFlush(source);
		} catch (DataIntegrityViolationException exception) {
			throw new ConflictException("Repository is already connected to this project.");
		}
		eventPublisher.publishEvent(new RepositorySyncEvent(
				projectId,
				source.getId(),
				RepositorySyncRequestType.REPOSITORY_ADDED,
				source.getTargetBranch(),
				source.getRepoUrl()
		));
		return RepositoryResponse.from(source);
	}

	@Transactional
	public RepositoryResponse updateRepository(Long projectId, Long repositoryId, RepositoryRequest request) {
		requireProject(projectId);
		ProjectSource source = findRepository(projectId, repositoryId);
		String repoUrl = request.url().trim();
		if (projectSourceRepository.existsByProjectIdAndRepoUrlAndIdNot(projectId, repoUrl, repositoryId)) {
			throw new ConflictException("Repository is already connected to this project.");
		}
		source.update(
				request.provider(), request.owner(), request.name(), repoUrl, request.defaultBranch(),
				joinPaths(request.includePaths()), joinPaths(request.excludePaths()), request.enabled()
		);
		// D2: repository_changed는 active commit 컬럼이 없어 아직 발행하지 않는다.
		// 변경된 원격 정보를 재동기화해야 하므로 상태를 PENDING으로 되돌린다.
		source.markPending();
		try {
			return RepositoryResponse.from(projectSourceRepository.saveAndFlush(source));
		} catch (DataIntegrityViolationException exception) {
			throw new ConflictException("Repository is already connected to this project.");
		}
	}

	@Transactional
	public void deleteRepository(Long projectId, Long repositoryId) {
		requireProject(projectId);
		ProjectSource source = findRepository(projectId, repositoryId);
		repositoryCredentialRepository.deleteByProjectSourceId(source.getId());
		projectSourceRepository.delete(source);
		eventPublisher.publishEvent(new RepositorySyncEvent(
				projectId,
				source.getId(),
				RepositorySyncRequestType.REPOSITORY_REMOVED,
				source.getTargetBranch(),
				source.getRepoUrl()
		));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markRepositorySyncing(Long projectId, Long repositoryId) {
		findRepository(projectId, repositoryId).markSyncing();
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markRepositorySyncFailed(Long projectId, Long repositoryId) {
		findRepository(projectId, repositoryId).markFailed();
	}

	@Transactional
	public void markRepositorySynced(Long projectId, Long repositoryId) {
		findRepository(projectId, repositoryId).markSynced();
		eventPublisher.publishEvent(new RepositorySyncCompletedEvent(projectId));
	}

	private Project requireProject(Long projectId) {
		return projectRepository.findById(projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
	}

	private ProjectSource findRepository(Long projectId, Long repositoryId) {
		return projectSourceRepository.findByIdAndProjectId(repositoryId, projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Repository not found: " + repositoryId));
	}

	private String joinPaths(List<String> paths) {
		return paths.stream().map(String::trim).filter(path -> !path.isBlank()).distinct().reduce((left, right) -> left + "\n" + right).orElse(null);
	}
}
