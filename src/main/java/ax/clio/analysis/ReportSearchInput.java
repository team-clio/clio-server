package ax.clio.analysis;

public record ReportSearchInput(
		String query,
		ReportSearchInputType type
) {
}
