package ax.clio.issue.dto;

import java.time.Instant;
import java.util.List;

public record IssueDetailResponse(
		Long id,
		Long projectId,
		String title,
		String summary,
		String status,
		String priority,
		String severity,
		Integer importanceScore,
		Integer riskScore,
		Integer reportCount,
		Instant firstSeenAt,
		Instant lastSeenAt,
		List<IssueReportResponse> reports
) {
}
