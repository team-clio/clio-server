package ax.clio.analysis.pipeline;

import java.util.List;

import ax.clio.report.BugReport;

/**
 * [파이프라인 포트] 점수화 단계. rule-based 점수·근거를 담은 draft를 생산한다.
 * 오케스트레이터에서 점수 로직을 완전히 분리하기 위한 포트(원칙 핵심).
 */
public interface Scorer {

	AnalysisResultDraft score(BugReport report, ReportSearchPreparation preparation,
			List<RankedCodeCandidate> candidates, List<CodeFlow> flows,
			List<SimilarIssueEntry> similarIssues, List<RelatedDecisionEntry> relatedDecisions);
}
