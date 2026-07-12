package ax.clio.analysis;

import java.util.List;

public record AnalysisResultDraft(
		int importanceScore,
		int difficultyScore,
		int riskScore,
		String issueType,
		String keywords,
		String domains,
		String summary,
		List<RelatedCodeEntry> relatedCode,
		List<CodeFlow> flows,
		String rationale,
		String recommendedFix,
		String recommendedTests,
		List<SimilarIssueEntry> similarIssues,
		List<RelatedDecisionEntry> relatedDecisions
) {

	/**
	 * LLM이 생성한 리포트 텍스트로 summary·recommendedFix·recommendedTests 3필드만 교체한 새 draft를 만든다
	 * (roadmap #10 / 4.3, L5). 점수·근거·검색결과는 그대로 유지한다(원칙).
	 */
	public AnalysisResultDraft withGeneratedReport(GeneratedReport generated) {
		return new AnalysisResultDraft(
				importanceScore,
				difficultyScore,
				riskScore,
				issueType,
				keywords,
				domains,
				generated.summary(),
				relatedCode,
				flows,
				rationale,
				generated.recommendedFix(),
				generated.recommendedTests(),
				similarIssues,
				relatedDecisions
		);
	}
}
