package ax.clio.bug.service;

import ax.clio.bug.dto.ApplyBugGroupingDecisionRequest;
import ax.clio.bug.dto.BugGroupingDecisionRequest;
import ax.clio.bug.dto.BugGroupingDecisionResponse;
import ax.clio.bug.entity.Bug;
import ax.clio.bug.entity.BugGroupingAction;
import ax.clio.bug.entity.BugGroupingDecision;
import ax.clio.bug.entity.BugOccurrence;
import ax.clio.bug.repository.BugGroupingDecisionRepository;
import ax.clio.bug.repository.BugOccurrenceRepository;
import ax.clio.bug.repository.BugRepository;
import ax.clio.common.ConflictException;
import ax.clio.common.ResourceNotFoundException;
import ax.clio.issue.entity.IssueBug;
import ax.clio.issue.repository.IssueBugRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BugGroupingDecisionService {

	private static final ObjectMapper SNAPSHOT_MAPPER = new ObjectMapper()
			.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

	private final BugOccurrenceRepository occurrenceRepository;
	private final BugRepository bugRepository;
	private final IssueBugRepository issueBugRepository;
	private final BugGroupingDecisionRepository decisionRepository;

	@Transactional
	public BugGroupingDecisionResponse apply(
			Long projectId,
			Long reportId,
			ApplyBugGroupingDecisionRequest request
	) {
		BugOccurrence report = occurrenceRepository.findByIdAndProjectId(reportId, projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Bug report not found: " + reportId));
		if (report.getBug() != null) {
			throw new ConflictException("Bug report is already grouped: " + reportId);
		}
		BugGroupingDecisionRequest grouping = request.groupingDecision();
		if (!reportId.equals(grouping.bugReportId())) {
			throw new ConflictException("Path reportId and grouping decision reportId do not match.");
		}

		Bug matchedBug = resolveMatchedBug(projectId, grouping);
		Bug resultingBug = switch (grouping.action()) {
			case MATCH_EXISTING -> attachExisting(report, matchedBug);
			case CREATE_NEW -> bugRepository.save(Bug.createFrom(report));
			case REVIEW -> null;
		};
		IssueBug resultingIssueBug = resultingBug == null
				? null
				: issueBugRepository.findByBugId(resultingBug.getId()).orElse(null);
		JsonNode snapshot = SNAPSHOT_MAPPER.valueToTree(grouping);
		BugGroupingDecision saved = decisionRepository.save(BugGroupingDecision.create(
				report,
				grouping.action(),
				matchedBug,
				resultingBug,
				grouping.confidence(),
				snapshot
		));
		return new BugGroupingDecisionResponse(
				saved.getId(),
				report.getId(),
				grouping.action(),
				matchedBug == null ? null : matchedBug.getId(),
				resultingBug == null ? null : resultingBug.getId(),
				resultingIssueBug == null ? null : resultingIssueBug.getIssue().getId(),
				resultingBug != null && resultingIssueBug == null
		);
	}

	private Bug resolveMatchedBug(Long projectId, BugGroupingDecisionRequest grouping) {
		if (grouping.matchedBugId() == null) {
			return null;
		}
		return bugRepository.findByIdAndProjectId(grouping.matchedBugId(), projectId)
				.orElseThrow(() -> new ResourceNotFoundException(
						"Matched bug not found: " + grouping.matchedBugId()
				));
	}

	private Bug attachExisting(BugOccurrence report, Bug target) {
		target.recordOccurrence(report);
		IssueBug issueBug = issueBugRepository.findByBugId(target.getId()).orElse(null);
		if (issueBug != null) {
			issueBug.getIssue().recordOccurrence(target, report.getOccurredAt());
		}
		return target;
	}
}
