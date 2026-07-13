package ax.clio.analysis.prepare;

import ax.clio.analysis.pipeline.ReportSearchPreparation;

import ax.clio.llm.entity.LlmConfig;
import ax.clio.report.entity.BugReport;

public interface ReportSearchPreparer {

	ReportSearchPreparation prepare(BugReport report, LlmConfig llmConfig, String model);
}
