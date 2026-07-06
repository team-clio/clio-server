package ax.clio.analysis;

public record AnalysisJobCreateRequest(
		Long llmConfigId,
		String model,
		ReportSearchInputMode searchMode
) {
}
