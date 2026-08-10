package ax.clio.issue.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import ax.clio.bug.entity.BugOccurrence;
import ax.clio.bug.entity.Priority;
import ax.clio.bug.entity.Severity;
import ax.clio.bug.repository.BugOccurrenceRepository;
import ax.clio.common.ResourceNotFoundException;
import ax.clio.common.dto.PageResponse;
import ax.clio.issue.dto.DailyReportCountResponse;
import ax.clio.issue.dto.IssueDetailResponse;
import ax.clio.issue.dto.IssueReportResponse;
import ax.clio.issue.dto.IssueStatsResponse;
import ax.clio.issue.dto.IssueSummaryResponse;
import ax.clio.issue.entity.Issue;
import ax.clio.issue.entity.IssueBug;
import ax.clio.issue.entity.IssueStatus;
import ax.clio.issue.repository.IssueBugRepository;
import ax.clio.issue.repository.IssueRepository;
import ax.clio.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IssueQueryService {

	private static final Set<String> SORT_FIELDS = Set.of(
			"lastSeenAt",
			"firstSeenAt",
			"updatedAt",
			"createdAt",
			"occurrenceCount",
			"importanceScore",
			"riskScore"
	);

	private final ProjectRepository projectRepository;
	private final IssueRepository issueRepository;
	private final IssueBugRepository issueBugRepository;
	private final BugOccurrenceRepository occurrenceRepository;

	@Transactional(readOnly = true)
	public PageResponse<IssueSummaryResponse> search(
			Long projectId,
			IssueStatus status,
			Priority priority,
			Severity severity,
			Instant from,
			Instant to,
			int page,
			int size,
			String sort
	) {
		requireProject(projectId);
		Specification<Issue> specification = filters(projectId, status, priority, severity, from, to);
		Page<Issue> result = issueRepository.findAll(
				specification,
				PageRequest.of(page, size, parseSort(sort))
		);
		return new PageResponse<>(
				result.getContent().stream().map(this::summary).toList(),
				result.getNumber(),
				result.getSize(),
				result.getTotalElements(),
				result.getTotalPages()
		);
	}

	@Transactional(readOnly = true)
	public IssueDetailResponse get(Long projectId, Long issueId) {
		Issue issue = issueRepository.findByIdAndProjectId(issueId, projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueId));
		List<IssueReportResponse> reports = issueBugRepository
				.findByIssueIdOrderByBugOccurrenceCountDesc(issueId)
				.stream()
				.flatMap(link -> occurrenceRepository
						.findByBugIdOrderByOccurredAtDesc(link.getBug().getId())
						.stream()
						.map(report -> report(link, report)))
				.sorted(java.util.Comparator.comparing(IssueReportResponse::occurredAt).reversed())
				.toList();
		return new IssueDetailResponse(
				issue.getId(),
				projectId,
				issue.getTitle(),
				issue.getSummary(),
				issue.getStatus().name(),
				enumName(issue.getPriority()),
				enumName(issue.getSeverity()),
				issue.getImportanceScore(),
				issue.getRiskScore(),
				issue.getOccurrenceCount(),
				issue.getFirstSeenAt(),
				issue.getLastSeenAt(),
				reports
		);
	}

	@Transactional(readOnly = true)
	public IssueStatsResponse stats(Long projectId, Instant from, Instant to) {
		requireProject(projectId);
		List<Issue> issues = issueRepository.findAll(filters(projectId, null, null, null, from, to));
		List<BugOccurrence> reports = occurrenceRepository.findLinkedForStats(projectId, from, to);
		Map<String, Long> bySeverity = enumCounts(
				Arrays.asList(Severity.values()),
				issues.stream().map(Issue::getSeverity).toList()
		);
		Map<String, Long> byPriority = enumCounts(
				Arrays.asList(Priority.values()),
				issues.stream().map(Issue::getPriority).toList()
		);
		List<DailyReportCountResponse> daily = reports.stream()
				.collect(Collectors.groupingBy(
						report -> report.getOccurredAt().atZone(ZoneOffset.UTC).toLocalDate(),
						java.util.TreeMap::new,
						Collectors.counting()
				))
				.entrySet()
				.stream()
				.map(entry -> new DailyReportCountResponse(entry.getKey(), entry.getValue()))
				.toList();
		return new IssueStatsResponse(
				issues.size(),
				count(issues, IssueStatus.OPEN),
				count(issues, IssueStatus.IN_PROGRESS),
				count(issues, IssueStatus.RESOLVED),
				reports.size(),
				bySeverity,
				byPriority,
				daily
		);
	}

	private Specification<Issue> filters(
			Long projectId,
			IssueStatus status,
			Priority priority,
			Severity severity,
			Instant from,
			Instant to
	) {
		return (root, query, criteria) -> {
			List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
			predicates.add(criteria.equal(root.get("project").get("id"), projectId));
			if (status != null) {
				predicates.add(criteria.equal(root.get("status"), status));
			}
			if (priority != null) {
				predicates.add(criteria.equal(root.get("priority"), priority));
			}
			if (severity != null) {
				predicates.add(criteria.equal(root.get("severity"), severity));
			}
			if (from != null) {
				predicates.add(criteria.greaterThanOrEqualTo(root.get("lastSeenAt"), from));
			}
			if (to != null) {
				predicates.add(criteria.lessThanOrEqualTo(root.get("lastSeenAt"), to));
			}
			return criteria.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
		};
	}

	private Sort parseSort(String value) {
		String[] parts = value.split(",", -1);
		if (parts.length != 2 || !SORT_FIELDS.contains(parts[0])) {
			throw new IllegalArgumentException("Unsupported issue sort: " + value);
		}
		Sort.Direction direction;
		try {
			direction = Sort.Direction.fromString(parts[1]);
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Unsupported issue sort direction: " + parts[1]);
		}
		return Sort.by(direction, parts[0]);
	}

	private IssueSummaryResponse summary(Issue issue) {
		return new IssueSummaryResponse(
				issue.getId(),
				issue.getProject().getId(),
				issue.getTitle(),
				issue.getSummary(),
				issue.getStatus().name(),
				enumName(issue.getPriority()),
				enumName(issue.getSeverity()),
				issue.getImportanceScore(),
				issue.getRiskScore(),
				issue.getOccurrenceCount(),
				issue.getFirstSeenAt(),
				issue.getLastSeenAt(),
				issue.getUpdatedAt()
		);
	}

	private IssueReportResponse report(IssueBug link, BugOccurrence report) {
		return new IssueReportResponse(
				report.getId(),
				report.getTitle(),
				report.getSource().name(),
				link.getGroupedBy().name(),
				link.getConfidence() == null ? null : link.getConfidence().doubleValue(),
				report.getOccurredAt()
		);
	}

	private long count(List<Issue> issues, IssueStatus status) {
		return issues.stream().filter(issue -> issue.getStatus() == status).count();
	}

	private <E extends Enum<E>> Map<String, Long> enumCounts(List<E> values, List<E> actual) {
		Map<String, Long> result = new LinkedHashMap<>();
		values.forEach(value -> result.put(value.name(), 0L));
		actual.stream().filter(java.util.Objects::nonNull)
				.forEach(value -> result.compute(value.name(), (key, count) -> count + 1));
		return result;
	}

	private String enumName(Enum<?> value) {
		return value == null ? null : value.name();
	}

	private void requireProject(Long projectId) {
		if (!projectRepository.existsById(projectId)) {
			throw new ResourceNotFoundException("Project not found: " + projectId);
		}
	}
}
