package ax.clio.analysis;

import java.time.Instant;
import java.util.List;

public record AnalysisJobResponse(
		Long id,
		Long reportId,
		Long llmConfigId,
		String llmModel,
		ReportSearchInputMode searchMode,
		AnalysisJobStatus status,
		Instant createdAt,
		Instant startedAt,
		Instant completedAt,
		String failureReason,
		Integer importanceScore,
		Integer difficultyScore,
		Integer riskScore,
		String issueType,
		String keywords,
		String domains,
		String summary,
		List<RelatedCodeEntry> relatedCode,
		String rationale,
		String recommendedFix,
		String recommendedTests
) {

	public static AnalysisJobResponse from(AnalysisJob job) {
		return new AnalysisJobResponse(
				job.getId(),
				job.getReport().getId(),
				job.getLlmConfigId(),
				job.getLlmModel(),
				job.getSearchMode(),
				job.getStatus(),
				job.getCreatedAt(),
				job.getStartedAt(),
				job.getCompletedAt(),
				job.getFailureReason(),
				job.getImportanceScore(),
				job.getDifficultyScore(),
				job.getRiskScore(),
				job.getIssueType(),
				job.getKeywords(),
				job.getDomains(),
				job.getSummary(),
				job.getRelatedCodeEntries(),
				job.getRationale(),
				job.getRecommendedFix(),
				job.getRecommendedTests()
		);
	}
}
