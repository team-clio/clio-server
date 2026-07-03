package ax.clio.analysis;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import ax.clio.code.CodeSearchResult;
import ax.clio.code.CodeSearchService;
import ax.clio.code.CodeSymbolType;
import ax.clio.report.BugReport;
import ax.clio.report.BugReportStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AnalysisWorker {

	private final AnalysisJobRepository analysisJobRepository;
	private final RuleBasedReportStructurer reportStructurer;
	private final CodeSearchService codeSearchService;

	public AnalysisWorker(AnalysisJobRepository analysisJobRepository, RuleBasedReportStructurer reportStructurer,
			CodeSearchService codeSearchService) {
		this.analysisJobRepository = analysisJobRepository;
		this.reportStructurer = reportStructurer;
		this.codeSearchService = codeSearchService;
	}

	@Transactional
	public void run(Long jobId) {
		AnalysisJob job = analysisJobRepository.findById(jobId).orElseThrow();
		try {
			job.start();
			job.getReport().changeStatus(BugReportStatus.ANALYZING);

			BugReport report = job.getReport();
			StructuredBugReport structured = reportStructurer.structure(report);
			List<CodeSearchResult> relatedCode = searchRelatedCode(report.getProject().getId(), structured);
			AnalysisResultDraft draft = buildDraft(report, structured, relatedCode);

			job.complete(draft);
			report.changeStatus(BugReportStatus.COMPLETED);
		} catch (Exception exception) {
			job.fail(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
			job.getReport().changeStatus(BugReportStatus.FAILED);
		}
	}

	private List<CodeSearchResult> searchRelatedCode(Long projectId, StructuredBugReport structured) {
		Set<String> queries = new LinkedHashSet<>();
		queries.addAll(structured.domains());
		queries.addAll(structured.keywords());
		return queries.stream()
				.flatMap(query -> codeSearchService.search(projectId, query, 10).stream())
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

	private AnalysisResultDraft buildDraft(BugReport report, StructuredBugReport structured, List<CodeSearchResult> relatedCode) {
		long relatedFileCount = relatedCode.stream().map(CodeSearchResult::filePath).distinct().count();
		boolean hasRelatedTest = relatedCode.stream().anyMatch(CodeSearchResult::test);
		boolean touchesEntity = relatedCode.stream().anyMatch(result -> result.symbolType() == CodeSymbolType.CLASS
				&& result.snippet() != null
				&& result.snippet().toLowerCase().contains("entity"));
		boolean touchesRepository = relatedCode.stream().anyMatch(result -> result.filePath().toLowerCase().contains("repository"));

		int importance = scoreImportance(structured);
		int difficulty = clamp(25 + (int) relatedFileCount * 8 + structured.domains().size() * 10 + (hasRelatedTest ? 0 : 10));
		int risk = clamp(20 + structured.domains().size() * 12 + (touchesEntity ? 20 : 0) + (touchesRepository ? 15 : 0)
				+ (hasRelatedTest ? 0 : 15));

		String related = relatedCode.stream()
				.map(result -> "- " + result.filePath() + ":" + result.lineNumber()
						+ " [" + result.matchType() + ", score=" + result.score() + "] "
						+ nullToEmpty(result.snippet()))
				.collect(Collectors.joining("\n"));

		String rationale = String.join("\n",
				"- 관련 파일 수: " + relatedFileCount,
				"- 감지 도메인: " + emptyAsDash(structured.domains()),
				"- 관련 테스트 코드 발견: " + (hasRelatedTest ? "예" : "아니오"),
				"- Repository 관련 코드 포함: " + (touchesRepository ? "예" : "아니오"),
				"- Entity 변경 가능성 신호: " + (touchesEntity ? "예" : "아니오")
		);

		String summary = "리포트 '" + report.getTitle() + "'는 " + structured.issueType()
				+ " 유형으로 분류되며, " + relatedFileCount + "개 파일 후보와 연결되었습니다.";

		String recommendedFix = "상위 점수 관련 코드부터 재현 경로를 확인하고, Controller-Service-Repository 흐름에서 상태 변경 또는 예외 처리 누락을 우선 검토하세요.";
		String recommendedTests = hasRelatedTest
				? "기존 관련 테스트를 확장해 리포트 재현 케이스와 회귀 케이스를 추가하세요."
				: "관련 테스트가 부족합니다. 서비스 단위 테스트와 주요 흐름 통합 테스트를 먼저 추가하세요.";

		return new AnalysisResultDraft(
				importance,
				difficulty,
				risk,
				structured.issueType().name(),
				String.join(",", structured.keywords()),
				String.join(",", structured.domains()),
				summary,
				related,
				rationale,
				recommendedFix,
				recommendedTests
		);
	}

	private int scoreImportance(StructuredBugReport structured) {
		int score = switch (structured.issueType()) {
			case INCIDENT -> 85;
			case BUG -> 70;
			case FEATURE_REQUEST -> 45;
			case IMPROVEMENT -> 40;
			case UNKNOWN -> 50;
		};
		for (String domain : structured.domains()) {
			if (domain.equals("PAYMENT") || domain.equals("AUTH") || domain.equals("ORDER")) {
				score += 10;
			}
		}
		return clamp(score);
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
