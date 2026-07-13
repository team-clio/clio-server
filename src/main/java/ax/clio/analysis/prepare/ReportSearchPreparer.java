package ax.clio.analysis.prepare;

import ax.clio.analysis.pipeline.ReportSearchPreparation;

import ax.clio.llm.LlmConfig;
import ax.clio.report.BugReport;

public interface ReportSearchPreparer {

	ReportSearchPreparation prepare(BugReport report, LlmConfig llmConfig, String model);
}
