package ax.clio.analysis.pipeline;

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
		List<RelatedDecisionEntry> relatedDecisions,
		List<String> evidenceWarnings
) implements java.io.Serializable {

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
				relatedDecisions,
				evidenceWarnings
		);
	}

	/**
	 * 근거 검증 경고(#11 / 4.4, E4)를 붙인 새 draft를 만든다. 텍스트는 그대로 두고 경고만 부착한다(E3=경고만).
	 */
	public AnalysisResultDraft withEvidenceWarnings(List<String> warnings) {
		return new AnalysisResultDraft(
				importanceScore,
				difficultyScore,
				riskScore,
				issueType,
				keywords,
				domains,
				summary,
				relatedCode,
				flows,
				rationale,
				recommendedFix,
				recommendedTests,
				similarIssues,
				relatedDecisions,
				warnings
		);
	}
}
