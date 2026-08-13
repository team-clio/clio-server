package ax.clio.issue.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import ax.clio.common.ResourceNotFoundException;
import ax.clio.issue.dto.CandidateBugLinkResponse;
import ax.clio.issue.dto.CandidateBugLinksRequest;
import ax.clio.issue.dto.IssueDetailResponse;
import ax.clio.issue.entity.IssueBug;
import ax.clio.issue.repository.IssueBugRepository;
import ax.clio.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InternalIssueQueryService {
	private final ProjectRepository projectRepository;
	private final IssueBugRepository issueBugRepository;
	private final IssueQueryService issueQueryService;

	@Transactional(readOnly = true)
	public IssueDetailResponse get(Long projectId, Long issueId) {
		return issueQueryService.get(projectId, issueId);
	}

	@Transactional(readOnly = true)
	public List<CandidateBugLinkResponse> links(Long projectId, CandidateBugLinksRequest request) {
		if (!projectRepository.existsById(projectId)) {
			throw new ResourceNotFoundException("Project not found: " + projectId);
		}
		Set<Long> requested = Set.copyOf(request.bugIds());
		return issueBugRepository.findByBugIdIn(request.bugIds()).stream()
				.filter(link -> link.getBug().getProject().getId().equals(projectId))
				.filter(link -> requested.contains(link.getBug().getId()))
				.collect(Collectors.toMap(
						link -> link.getBug().getId(),
						this::response,
						(first, ignored) -> first,
						java.util.LinkedHashMap::new
				))
				.values().stream().toList();
	}

	private CandidateBugLinkResponse response(IssueBug link) {
		return new CandidateBugLinkResponse(
				link.getBug().getId(),
				link.getIssue().getId(),
				link.getIssue().getTitle(),
				link.getIssue().getStatus().name(),
				link.getIssue().getSummary()
		);
	}
}
