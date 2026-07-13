package ax.clio.analysis.pipeline;

public record ReportSearchInput(
		String query,
		ReportSearchInputType type
) {
}
