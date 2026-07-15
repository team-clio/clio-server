package ax.clio.analysis.prepare;

import ax.clio.analysis.pipeline.contract.ReportSearchPreparation;

import ax.clio.llm.entity.LlmConfig;
import ax.clio.report.entity.BugReport;

public interface ReportSearchPreparer {

	ReportSearchPreparation prepare(BugReport report, LlmConfig llmConfig, String model);
}
