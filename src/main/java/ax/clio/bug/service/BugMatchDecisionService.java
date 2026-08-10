package ax.clio.bug.service;

import java.math.BigDecimal;

import ax.clio.bug.dto.ApplyMatchDecisionRequest;
import ax.clio.bug.dto.MatchDecisionRequest;
import ax.clio.bug.dto.MatchDecisionResponse;
import ax.clio.bug.entity.Bug;
import ax.clio.bug.entity.BugMatchAction;
import ax.clio.bug.entity.BugMatchDecision;
import ax.clio.bug.entity.BugOccurrence;
import ax.clio.bug.repository.BugMatchDecisionRepository;
import ax.clio.bug.repository.BugOccurrenceRepository;
import ax.clio.bug.repository.BugRepository;
import ax.clio.common.ConflictException;
import ax.clio.common.ResourceNotFoundException;
import ax.clio.issue.entity.Issue;
import ax.clio.issue.entity.IssueBug;
import ax.clio.issue.repository.IssueBugRepository;
import ax.clio.issue.repository.IssueRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BugMatchDecisionService {

	private static final ObjectMapper SNAPSHOT_MAPPER = new ObjectMapper()
			.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

	private final BugRepository bugRepository;
	private final BugOccurrenceRepository bugOccurrenceRepository;
	private final BugMatchDecisionRepository decisionRepository;
	private final IssueRepository issueRepository;
	private final IssueBugRepository issueBugRepository;

	@Transactional
	public MatchDecisionResponse apply(
			Long projectId,
			Long bugId,
			ApplyMatchDecisionRequest request
	) {
		Bug bug = bugRepository.findByIdAndProjectId(bugId, projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Bug not found: " + bugId));
		BugOccurrence report = bugOccurrenceRepository.findByIdAndBugIdAndBugProjectId(
				request.bugReportId(),
				bugId,
				projectId
		).orElseThrow(() -> new ResourceNotFoundException(
				"Bug report not found for bug: " + request.bugReportId()
		));
		MatchDecisionRequest match = request.matchDecision();
		if (!bugId.equals(match.bugId())) {
			throw new ConflictException("Path bugId and match decision bugId do not match.");
		}

		Issue matchedIssue = resolveMatchedIssue(projectId, match);
		LinkResult linkResult = switch (match.action()) {
			case AUTO_LINK -> linkExisting(bug, matchedIssue, match.confidence());
			case REVIEW -> new LinkResult(null, false);
			case CREATE_NEW -> createAndLink(bug, match.confidence());
		};
		JsonNode snapshot = SNAPSHOT_MAPPER.valueToTree(match);
		BugMatchDecision saved = decisionRepository.save(BugMatchDecision.create(
				bug,
				report,
				match.action(),
				matchedIssue,
				linkResult.issue(),
				match.confidence(),
				snapshot
		));
		return new MatchDecisionResponse(
				saved.getId(),
				bug.getId(),
				report.getId(),
				match.action(),
				matchedIssue == null ? null : matchedIssue.getId(),
				linkResult.issue() == null ? null : linkResult.issue().getId(),
				linkResult.linked()
		);
	}

	private Issue resolveMatchedIssue(Long projectId, MatchDecisionRequest match) {
		if (match.matchedIssueId() == null) {
			return null;
		}
		return issueRepository.findByIdAndProjectId(match.matchedIssueId(), projectId)
				.orElseThrow(() -> new ResourceNotFoundException(
						"Matched issue not found: " + match.matchedIssueId()
				));
	}

	private LinkResult linkExisting(Bug bug, Issue target, BigDecimal confidence) {
		IssueBug existing = issueBugRepository.findByBugId(bug.getId()).orElse(null);
		if (existing != null) {
			if (!existing.getIssue().getId().equals(target.getId())) {
				throw new ConflictException(
						"Bug is already linked to a different issue: " + existing.getIssue().getId()
				);
			}
			bug.markTriaged();
			return new LinkResult(existing.getIssue(), true);
		}
		return persistLink(bug, target, confidence);
	}

	private LinkResult createAndLink(Bug bug, BigDecimal confidence) {
		IssueBug existing = issueBugRepository.findByBugId(bug.getId()).orElse(null);
		if (existing != null) {
			throw new ConflictException(
					"Bug is already linked to issue: " + existing.getIssue().getId()
			);
		}
		Issue created = issueRepository.save(Issue.createFromBug(bug, confidence));
		return persistLink(bug, created, confidence);
	}

	private LinkResult persistLink(Bug bug, Issue issue, BigDecimal confidence) {
		issue.attach(bug, confidence);
		issueBugRepository.save(IssueBug.create(issue, bug, confidence));
		bug.markTriaged();
		return new LinkResult(issue, true);
	}

	private record LinkResult(Issue issue, boolean linked) {
	}
}
