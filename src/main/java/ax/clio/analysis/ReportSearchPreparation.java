package ax.clio.analysis;

import java.util.List;

public record ReportSearchPreparation(
		String reportType,
		List<String> businessTerms,
		List<String> candidateDomains,
		List<String> symptoms,
		List<String> codeSearchTerms,
		String confidence
) {

	public static ReportSearchPreparation rawOnly() {
		return new ReportSearchPreparation(
				"UNKNOWN",
				List.of(),
				List.of(),
				List.of(),
				List.of(),
				"LOW"
		);
	}
}
