package ax.clio.analysis;

import java.util.List;
import java.util.stream.Collectors;

import ax.clio.llm.LlmConfig;
import ax.clio.llm.LlmConfigService;
import ax.clio.memory.IssueMemoryService;
import ax.clio.memory.ScoredIssue;
import ax.clio.report.BugReport;
import ax.clio.report.BugReportStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AnalysisWorker {

	private static final int SIMILAR_ISSUE_LIMIT = 3;

	private final AnalysisJobRepository analysisJobRepository;
	private final CodeCandidateRanker codeCandidateRanker;
	private final FlowTracer flowTracer;
	private final ReportSearchInputBuilder searchInputBuilder;
	private final ReportSearchPreparer reportSearchPreparer;
	private final LlmConfigService llmConfigService;
	private final IssueMemoryService issueMemoryService;

	public AnalysisWorker(AnalysisJobRepository analysisJobRepository, CodeCandidateRanker codeCandidateRanker,
			FlowTracer flowTracer, ReportSearchInputBuilder searchInputBuilder, ReportSearchPreparer reportSearchPreparer,
			LlmConfigService llmConfigService, IssueMemoryService issueMemoryService) {
		this.analysisJobRepository = analysisJobRepository;
		this.codeCandidateRanker = codeCandidateRanker;
		this.flowTracer = flowTracer;
		this.searchInputBuilder = searchInputBuilder;
		this.reportSearchPreparer = reportSearchPreparer;
		this.llmConfigService = llmConfigService;
		this.issueMemoryService = issueMemoryService;
	}

	@Transactional
	public void run(Long jobId) {
		AnalysisJob job = analysisJobRepository.findById(jobId).orElseThrow();
		try {
			job.start();
			job.getReport().changeStatus(BugReportStatus.ANALYZING);

			BugReport report = job.getReport();
			ReportSearchPreparation preparation = prepare(report, job);
			List<ReportSearchInput> searchInputs = searchInputBuilder.build(report, preparation, job.getSearchMode());
			List<RankedCodeCandidate> candidates = codeCandidateRanker.rank(report.getProject().getId(), searchInputs);
			List<String> candidatePaths = candidates.stream().map(RankedCodeCandidate::filePath).distinct().toList();
			List<CodeFlow> flows = flowTracer.trace(report.getProject().getId(), candidatePaths);
			// #8: 유사 과거 이슈는 이번 리포트를 기억하기 전에 조회(과거분 기준). 자기 자신은 서비스가 제외.
			List<ScoredIssue> similarIssues = issueMemoryService.findSimilar(report, SIMILAR_ISSUE_LIMIT);
			AnalysisResultDraft draft = buildDraft(report, preparation, candidates, flows, similarIssues);

			job.complete(draft);
			report.changeStatus(BugReportStatus.COMPLETED);
			// #8: 분석된 리포트를 Issue Memory에 기억(이후 리포트가 유사이슈로 찾을 수 있게).
			issueMemoryService.remember(report);
		} catch (Exception exception) {
			job.fail(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
			job.getReport().changeStatus(BugReportStatus.FAILED);
		}
	}

	private ReportSearchPreparation prepare(BugReport report, AnalysisJob job) {
		if (job.getSearchMode() == ReportSearchInputMode.RAW_ONLY || job.getLlmConfigId() == null) {
			return ReportSearchPreparation.rawOnly();
		}
		LlmConfig config = llmConfigService.getConfig(job.getLlmConfigId());
		return reportSearchPreparer.prepare(report, config, job.getLlmModel());
	}

	private AnalysisResultDraft buildDraft(BugReport report, ReportSearchPreparation preparation,
			List<RankedCodeCandidate> candidates, List<CodeFlow> flows, List<ScoredIssue> similarIssues) {
		long relatedFileCount = candidates.stream().map(RankedCodeCandidate::filePath).distinct().count();
		boolean touchesEntity = candidates.stream()
				.anyMatch(c -> "ENTITY".equals(c.symbolRole()));
		boolean touchesRepository = candidates.stream()
				.anyMatch(c -> "REPOSITORY".equals(c.symbolRole()));

		// 흐름이 걸친 서로 다른 레이어 수(넓게 걸칠수록 수정 범위가 크다)
		int maxFlowLayerSpan = flows.stream()
				.mapToInt(AnalysisWorker::layerSpan)
				.max()
				.orElse(0);

		int importance = scoreImportance(preparation);
		int difficulty = clamp(25 + (int) relatedFileCount * 8
				+ preparation.candidateDomains().size() * 10
				+ maxFlowLayerSpan * 5);
		int risk = clamp(20 + preparation.candidateDomains().size() * 12
				+ (touchesEntity ? 20 : 0)
				+ (touchesRepository ? 15 : 0));

		List<RelatedCodeEntry> entries = candidates.stream()
				.map(c -> new RelatedCodeEntry(
						c.filePath(), c.symbolName(), c.symbolRole(),
						c.matchType() != null ? c.matchType().name() : null,
						c.lineNumber(), c.snippet(), c.adjustedScore(), c.hitCount()))
				.toList();

		String rationale = String.join("\n",
				"- 관련 파일 수: " + relatedFileCount,
				"- 후보 도메인: " + emptyAsDash(preparation.candidateDomains()),
				"- 검색어: " + emptyAsDash(preparation.codeSearchTerms()),
				"- 검색 입력 신뢰도: " + preparation.confidence(),
				"- Repository 관련 코드 포함: " + (touchesRepository ? "예" : "아니오"),
				"- Entity 변경 가능성 신호: " + (touchesEntity ? "예" : "아니오"),
				"- 영향 흐름: " + describeFlows(flows)
		);

		String summary = "리포트 '" + report.getTitle() + "'는 " + reportType(preparation)
				+ " 입력으로 해석되며, " + relatedFileCount + "개 파일 후보와 연결되었습니다.";

		String recommendedFix = "상위 점수 관련 코드부터 재현 경로를 확인하고, Controller-Service-Repository 흐름에서 상태 변경 또는 예외 처리 누락을 우선 검토하세요.";
		String recommendedTests = "서비스 단위 테스트와 주요 흐름 통합 테스트를 추가하세요.";

		List<SimilarIssueEntry> similarIssueEntries = similarIssues.stream()
				.map(scored -> new SimilarIssueEntry(
						scored.issue().getReport().getId(),
						scored.issue().getTitleSnapshot(),
						scored.score()))
				.toList();

		return new AnalysisResultDraft(
				importance,
				difficulty,
				risk,
				"UNKNOWN",
				String.join(",", searchTerms(preparation)),
				String.join(",", preparation.candidateDomains()),
				summary,
				entries,
				flows,
				rationale,
				recommendedFix,
				recommendedTests,
				similarIssueEntries
		);
	}

	private static int layerSpan(CodeFlow flow) {
		long distinctLayers = flow.nodes().stream()
				.map(FlowNode::role)
				.map(CodeDependencyGraph::layerOf)
				.filter(layer -> layer != CodeDependencyGraph.UNKNOWN_LAYER)
				.distinct()
				.count();
		return (int) distinctLayers;
	}

	private String describeFlows(List<CodeFlow> flows) {
		if (flows.isEmpty()) {
			return "-";
		}
		return flows.stream().map(CodeFlow::describe).collect(Collectors.joining(" | "));
	}

	private int scoreImportance(ReportSearchPreparation preparation) {
		int score = 50;
		for (String symptom : preparation.symptoms()) {
			if (symptom.equals("FAILED") || symptom.equals("ERROR") || symptom.equals("TIMEOUT")) {
				score += 15;
			}
			if (symptom.equals("UNAUTHORIZED") || symptom.equals("STATE_NOT_UPDATED")) {
				score += 10;
			}
		}
		for (String domain : preparation.candidateDomains()) {
			String normalized = domain.toLowerCase();
			if (normalized.contains("payment") || normalized.contains("auth") || normalized.contains("order")) {
				score += 10;
			}
		}
		return clamp(score);
	}

	private List<String> searchTerms(ReportSearchPreparation preparation) {
		return java.util.stream.Stream.concat(preparation.businessTerms().stream(), preparation.codeSearchTerms().stream())
				.distinct()
				.toList();
	}

	private String reportType(ReportSearchPreparation preparation) {
		return preparation.reportType() == null || preparation.reportType().isBlank() ? "UNKNOWN" : preparation.reportType();
	}

	private int clamp(int score) {
		return Math.max(0, Math.min(100, score));
	}

	private String emptyAsDash(List<String> values) {
		return values.isEmpty() ? "-" : String.join(",", values);
	}
}
