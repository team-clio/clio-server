package ax.clio.analysis.job.dto;

import ax.clio.analysis.pipeline.contract.ReportSearchInputMode;

public record AnalysisJobCreateRequest(
		Long llmConfigId,
		String model,
		ReportSearchInputMode searchMode
) {
}
