package ax.clio.analysis.job;

import ax.clio.analysis.pipeline.ReportSearchInputMode;

public record AnalysisJobCreateRequest(
		Long llmConfigId,
		String model,
		ReportSearchInputMode searchMode
) {
}
