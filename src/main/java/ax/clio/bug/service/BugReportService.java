package ax.clio.bug.service;

import java.time.Instant;

import ax.clio.bug.dto.AgentBugReportResponse;
import ax.clio.bug.dto.BugReportCollectRequest;
import ax.clio.bug.dto.BugReportResponse;
import ax.clio.bug.dto.BugReportSummaryResponse;
import ax.clio.bug.dto.GroupingResponse;
import ax.clio.bug.entity.Bug;
import ax.clio.bug.entity.BugOccurrence;
import ax.clio.bug.entity.BugSource;
import ax.clio.bug.entity.BugStatus;
import ax.clio.bug.entity.Severity;
import ax.clio.bug.repository.BugOccurrenceRepository;
import ax.clio.common.ResourceNotFoundException;
import ax.clio.common.dto.PageResponse;
import ax.clio.issue.entity.IssueBug;
import ax.clio.issue.repository.IssueBugRepository;
import ax.clio.project.entity.Project;
import ax.clio.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class BugReportService {

	private static final com.fasterxml.jackson.databind.ObjectMapper PERSISTENCE_MAPPER =
			new com.fasterxml.jackson.databind.ObjectMapper();

	private final ProjectRepository projectRepository;
	private final BugOccurrenceRepository occurrenceRepository;
	private final IssueBugRepository issueBugRepository;
	private final ObjectMapper objectMapper;

	@Transactional
	public BugReportResponse collect(Long projectId, BugReportCollectRequest request) {
		Project project = projectRepository.findById(projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
		BugOccurrence occurrence = occurrenceRepository.save(BugOccurrence.collect(
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
		return response(occurrence);
	}

	@Transactional(readOnly = true)
	public AgentBugReportResponse getForAgent(Long projectId, Long reportId) {
		BugOccurrence occurrence = find(projectId, reportId);
		JsonNode rawPayload = occurrence.getRawPayload() == null
				? objectMapper.createObjectNode()
				: objectMapper.readTree(occurrence.getRawPayload().toString());
		return new AgentBugReportResponse(
				occurrence.getId(),
				occurrence.getTitle(),
				occurrence.getDescription(),
				occurrence.getSource().name(),
				occurrence.getErrorType(),
				occurrence.getMessage(),
				occurrence.stackTraceValues(),
				occurrence.getOccurredAt(),
				rawPayload
		);
	}

	@Transactional(readOnly = true)
	public PageResponse<BugReportSummaryResponse> search(
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
		Page<BugOccurrence> result = occurrenceRepository.search(
				projectId,
				from,
				to,
				issueId,
				source,
				status,
				severity,
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

	private BugReportResponse response(BugOccurrence occurrence) {
		Bug bug = occurrence.getBug();
		IssueBug issueBug = bug == null ? null : issueBugRepository.findByBugId(bug.getId()).orElse(null);
		Long issueId = issueBug == null ? null : issueBug.getIssue().getId();
		return new BugReportResponse(
				occurrence.getId(),
				occurrence.getProject().getId(),
				bug == null ? null : bug.getId(),
				issueId,
				occurrence.getTitle(),
				occurrence.getSource().name(),
				occurrence.getErrorType(),
				occurrence.firstStackFrame(),
				bug == null ? "PENDING_GROUPING" : bug.getStatus().name(),
				occurrence.getOccurredAt(),
				occurrence.getCreatedAt(),
				grouping(issueBug)
		);
	}

	private BugReportSummaryResponse summary(BugOccurrence occurrence) {
		Bug bug = occurrence.getBug();
		IssueBug issueBug = bug == null ? null : issueBugRepository.findByBugId(bug.getId()).orElse(null);
		return new BugReportSummaryResponse(
				occurrence.getId(),
				occurrence.getProject().getId(),
				bug == null ? null : bug.getId(),
				issueBug == null ? null : issueBug.getIssue().getId(),
				occurrence.getTitle(),
				occurrence.getSource().name(),
				occurrence.getErrorType(),
				occurrence.firstStackFrame(),
				bug == null ? "PENDING_GROUPING" : bug.getStatus().name(),
				bug == null || bug.getSeverity() == null ? null : bug.getSeverity().name(),
				occurrence.getOccurredAt(),
				occurrence.getCreatedAt()
		);
	}

	private GroupingResponse grouping(IssueBug issueBug) {
		if (issueBug == null) {
			return new GroupingResponse(false, null, null, null);
		}
		return new GroupingResponse(
				true,
				issueBug.getIssue().getId(),
				issueBug.getGroupedBy().name(),
				issueBug.getConfidence() == null ? null : issueBug.getConfidence().doubleValue()
		);
	}

	private BugOccurrence find(Long projectId, Long reportId) {
		return occurrenceRepository.findByIdAndProjectId(reportId, projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Bug report not found: " + reportId));
	}

	private BugSource parseSource(String source) {
		try {
			return BugSource.valueOf(source.trim().toUpperCase());
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Unsupported bug report source: " + source, exception);
		}
	}

	private com.fasterxml.jackson.databind.JsonNode persistenceTree(JsonNode node) {
		if (node == null) {
			return null;
		}
		try {
			return PERSISTENCE_MAPPER.readTree(node.toString());
		} catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
			throw new IllegalStateException("Bug report raw payload cannot be persisted.", exception);
		}
	}
}
