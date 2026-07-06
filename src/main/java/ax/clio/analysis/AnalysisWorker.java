package ax.clio.analysis;

import java.util.List;
import java.util.stream.Collectors;

import ax.clio.code.CodeSearchResult;
import ax.clio.code.CodeSearchService;
import ax.clio.code.CodeSymbolType;
import ax.clio.llm.LlmConfig;
import ax.clio.llm.LlmConfigService;
import ax.clio.report.BugReport;
import ax.clio.report.BugReportStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AnalysisWorker {

	private final AnalysisJobRepository analysisJobRepository;
	private final CodeSearchService codeSearchService;
	private final ReportSearchInputBuilder searchInputBuilder;
	private final ReportSearchPreparer reportSearchPreparer;
	private final LlmConfigService llmConfigService;

	public AnalysisWorker(AnalysisJobRepository analysisJobRepository, CodeSearchService codeSearchService,
			ReportSearchInputBuilder searchInputBuilder, ReportSearchPreparer reportSearchPreparer,
			LlmConfigService llmConfigService) {
		this.analysisJobRepository = analysisJobRepository;
		this.codeSearchService = codeSearchService;
		this.searchInputBuilder = searchInputBuilder;
		this.reportSearchPreparer = reportSearchPreparer;
		this.llmConfigService = llmConfigService;
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
			List<CodeSearchResult> relatedCode = searchRelatedCode(report.getProject().getId(), searchInputs);
			AnalysisResultDraft draft = buildDraft(report, preparation, relatedCode);

			job.complete(draft);
			report.changeStatus(BugReportStatus.COMPLETED);
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

	private List<CodeSearchResult> searchRelatedCode(Long projectId, List<ReportSearchInput> searchInputs) {
		return searchInputs.stream()
				.flatMap(input -> codeSearchService.search(projectId, input.query(), 10).stream())
				.collect(Collectors.toMap(
						result -> result.filePath() + ":" + result.lineNumber() + ":" + result.matchType(),
						result -> result,
						(left, right) -> left.score() >= right.score() ? left : right
				))
				.values()
				.stream()
				.sorted((left, right) -> Integer.compare(right.score(), left.score()))
				.limit(20)
				.toList();
	}

	private AnalysisResultDraft buildDraft(BugReport report, ReportSearchPreparation preparation,
			List<CodeSearchResult> relatedCode) {
		long relatedFileCount = relatedCode.stream().map(CodeSearchResult::filePath).distinct().count();
		boolean hasRelatedTest = relatedCode.stream().anyMatch(CodeSearchResult::test);
		boolean touchesEntity = relatedCode.stream().anyMatch(result -> result.symbolType() == CodeSymbolType.CLASS
				&& result.snippet() != null
				&& result.snippet().toLowerCase().contains("entity"));
		boolean touchesRepository = relatedCode.stream().anyMatch(result -> result.filePath().toLowerCase().contains("repository"));

		int importance = scoreImportance(preparation);
		int difficulty = clamp(25 + (int) relatedFileCount * 8 + preparation.candidateDomains().size() * 10
				+ (hasRelatedTest ? 0 : 10));
		int risk = clamp(20 + preparation.candidateDomains().size() * 12 + (touchesEntity ? 20 : 0) + (touchesRepository ? 15 : 0)
				+ (hasRelatedTest ? 0 : 15));

		String related = relatedCode.stream()
				.map(result -> "- " + result.filePath() + ":" + result.lineNumber()
						+ " [" + result.matchType() + ", score=" + result.score() + "] "
						+ nullToEmpty(result.snippet()))
				.collect(Collectors.joining("\n"));

		String rationale = String.join("\n",
				"- 관련 파일 수: " + relatedFileCount,
				"- LLM 후보 도메인: " + emptyAsDash(preparation.candidateDomains()),
				"- LLM 검색어: " + emptyAsDash(preparation.codeSearchTerms()),
				"- 검색 입력 신뢰도: " + preparation.confidence(),
				"- 관련 테스트 코드 발견: " + (hasRelatedTest ? "예" : "아니오"),
				"- Repository 관련 코드 포함: " + (touchesRepository ? "예" : "아니오"),
				"- Entity 변경 가능성 신호: " + (touchesEntity ? "예" : "아니오")
		);

		String summary = "리포트 '" + report.getTitle() + "'는 " + reportType(preparation)
				+ " 입력으로 해석되며, " + relatedFileCount + "개 파일 후보와 연결되었습니다.";

		String recommendedFix = "상위 점수 관련 코드부터 재현 경로를 확인하고, Controller-Service-Repository 흐름에서 상태 변경 또는 예외 처리 누락을 우선 검토하세요.";
		String recommendedTests = hasRelatedTest
				? "기존 관련 테스트를 확장해 리포트 재현 케이스와 회귀 케이스를 추가하세요."
				: "관련 테스트가 부족합니다. 서비스 단위 테스트와 주요 흐름 통합 테스트를 먼저 추가하세요.";

		return new AnalysisResultDraft(
				importance,
				difficulty,
				risk,
				"UNKNOWN",
				String.join(",", searchTerms(preparation)),
				String.join(",", preparation.candidateDomains()),
				summary,
				related,
				rationale,
				recommendedFix,
				recommendedTests
		);
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

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
