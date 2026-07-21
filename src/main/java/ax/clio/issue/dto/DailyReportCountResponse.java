package ax.clio.issue.dto;

import java.time.LocalDate;

public record DailyReportCountResponse(
		LocalDate date,
		long count
) {
}
