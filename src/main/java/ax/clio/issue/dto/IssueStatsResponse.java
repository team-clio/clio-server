package ax.clio.issue.dto;

import java.util.List;
import java.util.Map;

public record IssueStatsResponse(
		long totalIssues,
		long openIssues,
		long inProgressIssues,
		long resolvedIssues,
		long totalReports,
		Map<String, Long> bySeverity,
		Map<String, Long> byPriority,
		List<DailyReportCountResponse> dailyReports
) {
}
