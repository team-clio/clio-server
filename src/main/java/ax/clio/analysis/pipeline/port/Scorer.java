package ax.clio.analysis.pipeline.port;

import ax.clio.analysis.pipeline.contract.AnalysisResultDraft;
import ax.clio.analysis.pipeline.contract.CodeFlow;
import ax.clio.analysis.pipeline.contract.RankedCodeCandidate;
import ax.clio.analysis.pipeline.contract.RelatedDecisionEntry;
import ax.clio.analysis.pipeline.contract.ReportSearchPreparation;
import ax.clio.analysis.pipeline.contract.SimilarIssueEntry;

import java.util.List;

import ax.clio.report.entity.BugReport;

/**
 * [파이프라인 포트] 점수화 단계. rule-based 점수·근거를 담은 draft를 생산한다.
 * 오케스트레이터에서 점수 로직을 완전히 분리하기 위한 포트(원칙 핵심).
 */
public interface Scorer {

	AnalysisResultDraft score(BugReport report, ReportSearchPreparation preparation,
			List<RankedCodeCandidate> candidates, List<CodeFlow> flows,
			List<SimilarIssueEntry> similarIssues, List<RelatedDecisionEntry> relatedDecisions);
}
