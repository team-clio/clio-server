package ax.clio.analysis.pipeline.contract;

public record ReportSearchInput(
		String query,
		ReportSearchInputType type
) {
}
