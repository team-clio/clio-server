package ax.clio.project.service;

import java.util.List;
import java.util.UUID;

import ax.clio.agent.client.RepositorySyncRequestType;
import ax.clio.agent.client.ClioAgentClient;
import ax.clio.agent.client.ClioAgentProperties;
import ax.clio.agent.event.RepositorySyncEvent;
import ax.clio.agent.event.RepositorySyncCompletedEvent;
import ax.clio.analysis.repository.AnalysisResultRepository;
import ax.clio.bug.repository.BugRepository;
import ax.clio.common.ConflictException;
import ax.clio.common.ResourceNotFoundException;
import ax.clio.issue.repository.IssueBranchRepository;
import ax.clio.issue.repository.IssueBugRepository;
import ax.clio.issue.repository.IssueRepository;
import ax.clio.mcp.repository.ApiKeyRepository;
import ax.clio.project.dto.CreateProjectRequest;
import ax.clio.project.dto.ProjectResponse;
import ax.clio.project.dto.RepositoryRequest;
import ax.clio.project.dto.RepositoryResponse;
import ax.clio.project.dto.UpdateProjectRequest;
import ax.clio.project.entity.Project;
import ax.clio.project.entity.ProjectSource;
import ax.clio.project.repository.ProjectRepository;
import ax.clio.project.repository.ProjectContextRepository;
import ax.clio.project.repository.ProjectSourceRepository;
import ax.clio.project.repository.RepositoryCredentialRepository;
import ax.clio.workflow.repository.AgentWorkflowRunRepository;
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
	private final ProjectContextRepository projectContextRepository;
	private final RepositoryCredentialRepository repositoryCredentialRepository;
	private final BugRepository bugRepository;
	private final IssueRepository issueRepository;
	private final IssueBugRepository issueBugRepository;
	private final IssueBranchRepository issueBranchRepository;
	private final AgentWorkflowRunRepository agentWorkflowRunRepository;
	private final AnalysisResultRepository analysisResultRepository;
	private final ApiKeyRepository apiKeyRepository;
	private final ClioAgentClient agentClient;
	private final ClioAgentProperties agentProperties;
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

	@Transactional
	public void deleteProject(Long projectId) {
		Project project = projectRepository.findByIdForUpdate(projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
		if (agentProperties.enabled()) {
			agentClient.deleteProjectData(projectId);
		}
		analysisResultRepository.clearPreviousAnalysisResultByProjectId(projectId);
		analysisResultRepository.deleteByWorkflowRunProjectId(projectId);
		agentWorkflowRunRepository.deleteByProjectId(projectId);
		issueBranchRepository.deleteByIssueProjectId(projectId);
		issueBugRepository.deleteByIssueProjectId(projectId);
		issueRepository.deleteByProjectId(projectId);
		bugRepository.deleteByProjectId(projectId);
		apiKeyRepository.deleteByProjectId(projectId);
		projectContextRepository.deleteByProjectId(projectId);
		repositoryCredentialRepository.deleteByProjectSourceProjectId(projectId);
		projectSourceRepository.deleteByProjectId(projectId);
		projectRepository.delete(project);
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
		publishRepositorySync(source, RepositorySyncRequestType.REPOSITORY_ADDED);
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
		// 연결 정보나 분석 범위가 바뀌면 전체 snapshot을 다시 만든다.
		source.markPending();
		try {
			source = projectSourceRepository.saveAndFlush(source);
		} catch (DataIntegrityViolationException exception) {
			throw new ConflictException("Repository is already connected to this project.");
		}
		if (source.isEnabled()) {
			publishRepositorySync(source, RepositorySyncRequestType.REPOSITORY_ADDED);
		}
		return RepositoryResponse.from(source);
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
				repositoryRequestId(projectId, source.getId()),
				source.getTargetBranch(),
				source.getRepoUrl(),
				List.of(),
				List.of()
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

	@Transactional(propagation = Propagation.REQUIRES_NEW)
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

	private void publishRepositorySync(ProjectSource source, RepositorySyncRequestType requestType) {
		RepositoryResponse repository = RepositoryResponse.from(source);
		eventPublisher.publishEvent(new RepositorySyncEvent(
				source.getProject().getId(),
				source.getId(),
				requestType,
				repositoryRequestId(source.getProject().getId(), source.getId()),
				source.getTargetBranch(),
				source.getRepoUrl(),
				repository.includePaths(),
				repository.excludePaths()
		));
	}

	private String repositoryRequestId(Long projectId, Long repositoryId) {
		return "repository-" + projectId + "-" + repositoryId + "-" + UUID.randomUUID();
	}
}
