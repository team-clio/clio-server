package ax.clio.analysis;

import ax.clio.llm.LlmConfig;
import ax.clio.report.BugReport;

public interface ReportSearchPreparer {

	ReportSearchPreparation prepare(BugReport report, LlmConfig llmConfig, String model);
}
