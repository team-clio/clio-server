package ax.clio.bug.service;

import java.time.Instant;
import java.util.ArrayList;

import ax.clio.bug.dto.AgentBugResponse;
import ax.clio.bug.dto.BugCollectRequest;
import ax.clio.bug.dto.BugResponse;
import ax.clio.bug.dto.BugSummaryResponse;
import ax.clio.bug.dto.GroupingResponse;
import ax.clio.bug.entity.Bug;
import ax.clio.bug.entity.BugSource;
import ax.clio.bug.entity.BugStatus;
import ax.clio.bug.entity.Severity;
import ax.clio.bug.repository.BugRepository;
import ax.clio.common.ResourceNotFoundException;
import ax.clio.common.dto.PageResponse;
import ax.clio.issue.entity.IssueBug;
import ax.clio.issue.repository.IssueBugRepository;
import ax.clio.project.entity.Project;
import ax.clio.project.repository.ProjectRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class BugService {
	private static final com.fasterxml.jackson.databind.ObjectMapper PERSISTENCE_MAPPER =
			new com.fasterxml.jackson.databind.ObjectMapper();

	private final ProjectRepository projectRepository;
	private final BugRepository bugRepository;
	private final IssueBugRepository issueBugRepository;
	private final ObjectMapper objectMapper;

	@Transactional
	public BugResponse collect(Long projectId, BugCollectRequest request) {
		Project project = projectRepository.findById(projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
		Bug bug = bugRepository.save(Bug.collect(
				project,
				parseSource(request.source()),
				request.title(),
				request.description(),
				request.errorType(),
				request.message(),
				request.stackTrace(),
				persistenceTree(request.rawPayload()),
				request.occurredAt()
		));
		return response(bug);
	}

	@Transactional(readOnly = true)
	public AgentBugResponse getForAgent(Long projectId, Long bugId) {
		Bug bug = find(projectId, bugId);
		return agentResponse(bug);
	}

	@Transactional(readOnly = true)
	public java.util.List<AgentBugResponse> listForAgent(Long projectId, Long afterBugId, int limit) {
		if (!projectRepository.existsById(projectId)) {
			throw new ResourceNotFoundException("Project not found: " + projectId);
		}
		return bugRepository.findByProjectIdAndIdGreaterThanOrderByIdAsc(
				projectId, afterBugId, PageRequest.of(0, limit)
		).stream().map(this::agentResponse).toList();
	}

	private AgentBugResponse agentResponse(Bug bug) {
		JsonNode rawPayload = bug.getRawPayload() == null
				? objectMapper.createObjectNode()
				: objectMapper.readTree(bug.getRawPayload().toString());
		return new AgentBugResponse(
				bug.getId(),
				bug.getProject().getId(),
				bug.getTitle(),
				bug.getDescription(),
				bug.getSource().name(),
				bug.getErrorType(),
				bug.getMessage(),
				bug.stackTraceValues(),
				bug.getOccurredAt(),
				rawPayload
		);
	}

	@Transactional(readOnly = true)
	public PageResponse<BugSummaryResponse> search(
			Long projectId,
			Instant from,
			Instant to,
			Long issueId,
			BugSource source,
			BugStatus status,
			Severity severity,
			int page,
			int size
	) {
		if (!projectRepository.existsById(projectId)) {
			throw new ResourceNotFoundException("Project not found: " + projectId);
		}
		Page<Bug> result = bugRepository.findAll(
				filters(projectId, from, to, issueId, source, status, severity),
				PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"))
		);
		return new PageResponse<>(
				result.getContent().stream().map(this::summary).toList(),
				result.getNumber(),
				result.getSize(),
				result.getTotalElements(),
				result.getTotalPages()
		);
	}

	private Specification<Bug> filters(
			Long projectId,
			Instant from,
			Instant to,
			Long issueId,
			BugSource source,
			BugStatus status,
			Severity severity
	) {
		return (root, query, criteria) -> {
			var predicates = new ArrayList<Predicate>();
			predicates.add(criteria.equal(root.get("project").get("id"), projectId));
			if (from != null) predicates.add(criteria.greaterThanOrEqualTo(root.get("occurredAt"), from));
			if (to != null) predicates.add(criteria.lessThanOrEqualTo(root.get("occurredAt"), to));
			if (source != null) predicates.add(criteria.equal(root.get("source"), source));
			if (status != null) predicates.add(criteria.equal(root.get("status"), status));
			if (severity != null) predicates.add(criteria.equal(root.get("severity"), severity));
			if (issueId != null) {
				var subquery = query.subquery(Long.class);
				var issueBug = subquery.from(IssueBug.class);
				subquery.select(issueBug.get("bug").get("id"))
						.where(criteria.equal(issueBug.get("issue").get("id"), issueId));
				predicates.add(root.get("id").in(subquery));
			}
			return criteria.and(predicates.toArray(Predicate[]::new));
		};
	}

	private BugResponse response(Bug bug) {
		IssueBug link = issueBugRepository.findByBugId(bug.getId()).orElse(null);
		return new BugResponse(
				bug.getId(),
				bug.getProject().getId(),
				link == null ? null : link.getIssue().getId(),
				bug.getTitle(),
				bug.getSource().name(),
				bug.getErrorType(),
				bug.firstStackFrame(),
				bug.getStatus().name(),
				bug.getSeverity() == null ? null : bug.getSeverity().name(),
				bug.getOccurredAt(),
				bug.getCreatedAt(),
				grouping(link)
		);
	}

	private BugSummaryResponse summary(Bug bug) {
		IssueBug link = issueBugRepository.findByBugId(bug.getId()).orElse(null);
		return new BugSummaryResponse(
				bug.getId(),
				bug.getProject().getId(),
				link == null ? null : link.getIssue().getId(),
				bug.getTitle(),
				bug.getSource().name(),
				bug.getErrorType(),
				bug.firstStackFrame(),
				bug.getStatus().name(),
				bug.getSeverity() == null ? null : bug.getSeverity().name(),
				bug.getOccurredAt(),
				bug.getCreatedAt()
		);
	}

	private GroupingResponse grouping(IssueBug link) {
		if (link == null) return new GroupingResponse(false, null, null, null);
		return new GroupingResponse(
				true,
				link.getIssue().getId(),
				link.getGroupedBy().name(),
				link.getConfidence() == null ? null : link.getConfidence().doubleValue()
		);
	}

	private Bug find(Long projectId, Long bugId) {
		return bugRepository.findByIdAndProjectId(bugId, projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Bug not found: " + bugId));
	}

	private BugSource parseSource(String source) {
		try {
			return BugSource.valueOf(source.trim().toUpperCase());
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Unsupported bug source: " + source, exception);
		}
	}

	private com.fasterxml.jackson.databind.JsonNode persistenceTree(JsonNode node) {
		if (node == null) return null;
		try {
			return PERSISTENCE_MAPPER.readTree(node.toString());
		} catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
			throw new IllegalStateException("Bug raw payload cannot be persisted.", exception);
		}
	}
}
