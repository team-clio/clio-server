package ax.clio.analysis;

import java.util.List;

import ax.clio.memory.DecisionMemoryService;
import ax.clio.memory.IssueMemoryService;
import ax.clio.report.BugReport;
import org.springframework.stereotype.Component;

/** #8 유사 이슈 + #9 관련 결정 조회를 감싸 entry로 매핑하는 {@link MemoryRetriever} 구현. */
@Component
class DefaultMemoryRetriever implements MemoryRetriever {

	private static final int SIMILAR_ISSUE_LIMIT = 3;
	private static final int RELATED_DECISION_LIMIT = 3;

	private final IssueMemoryService issueMemoryService;
	private final DecisionMemoryService decisionMemoryService;

	DefaultMemoryRetriever(IssueMemoryService issueMemoryService, DecisionMemoryService decisionMemoryService) {
		this.issueMemoryService = issueMemoryService;
		this.decisionMemoryService = decisionMemoryService;
	}

	@Override
	public MemoryContext retrieve(BugReport report) {
		List<SimilarIssueEntry> similarIssues = issueMemoryService.findSimilar(report, SIMILAR_ISSUE_LIMIT).stream()
				.map(scored -> new SimilarIssueEntry(
						scored.issue().getReport().getId(), scored.issue().getTitleSnapshot(), scored.score()))
				.toList();
		List<RelatedDecisionEntry> relatedDecisions = decisionMemoryService.findRelevant(report, RELATED_DECISION_LIMIT)
				.stream()
				.map(scored -> new RelatedDecisionEntry(
						scored.decision().getId(), scored.decision().getTitle(), scored.score()))
				.toList();
		return new MemoryContext(similarIssues, relatedDecisions);
	}
}
