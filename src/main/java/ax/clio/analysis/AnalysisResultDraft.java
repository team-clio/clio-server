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
		String recommendedTests
) {
}
