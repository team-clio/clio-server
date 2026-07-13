package ax.clio.analysis.scoring;

import ax.clio.analysis.flow.CodeDependencyGraph;
import ax.clio.analysis.pipeline.AnalysisGraph;
import ax.clio.analysis.pipeline.AnalysisResultDraft;
import ax.clio.analysis.pipeline.CodeFlow;
import ax.clio.analysis.pipeline.FlowNode;
import ax.clio.analysis.pipeline.RankedCodeCandidate;
import ax.clio.analysis.pipeline.RelatedCodeEntry;
import ax.clio.analysis.pipeline.RelatedDecisionEntry;
import ax.clio.analysis.pipeline.ReportSearchPreparation;
import ax.clio.analysis.pipeline.Scorer;
import ax.clio.analysis.pipeline.SimilarIssueEntry;

import java.util.List;
import java.util.stream.Collectors;

import ax.clio.report.entity.BugReport;
import org.springframework.stereotype.Component;

/**
 * rule-based 점수화 {@link Scorer} 구현. 이전에 {@code AnalysisGraph.buildDraft}에 있던 로직을 그대로 옮긴 것으로,
 * 오케스트레이터에서 점수 로직을 완전히 분리한다(포트화 원칙 핵심). 동작은 기존과 동일하다.
 */
@Component
public class RuleBasedScorer implements Scorer {

	@Override
	public AnalysisResultDraft score(BugReport report, ReportSearchPreparation preparation,
			List<RankedCodeCandidate> candidates, List<CodeFlow> flows,
			List<SimilarIssueEntry> similarIssues, List<RelatedDecisionEntry> relatedDecisions) {
		long relatedFileCount = candidates.stream().map(RankedCodeCandidate::filePath).distinct().count();
		boolean touchesEntity = candidates.stream()
				.anyMatch(c -> "ENTITY".equals(c.symbolRole()));
		boolean touchesRepository = candidates.stream()
				.anyMatch(c -> "REPOSITORY".equals(c.symbolRole()));

		// 흐름이 걸친 서로 다른 레이어 수(넓게 걸칠수록 수정 범위가 크다)
		int maxFlowLayerSpan = flows.stream()
				.mapToInt(RuleBasedScorer::layerSpan)
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
				similarIssues,
				relatedDecisions,
				List.of()
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
