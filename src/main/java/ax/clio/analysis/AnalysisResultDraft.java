package ax.clio.analysis;

public record AnalysisResultDraft(
		int importanceScore,
		int difficultyScore,
		int riskScore,
		String issueType,
		String keywords,
		String domains,
		String summary,
		String relatedCode,
		String rationale,
		String recommendedFix,
		String recommendedTests
) {
}
