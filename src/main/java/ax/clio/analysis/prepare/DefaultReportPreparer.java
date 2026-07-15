package ax.clio.analysis.prepare;

import ax.clio.analysis.job.entity.AnalysisJob;
import ax.clio.analysis.pipeline.port.ReportPreparer;
import ax.clio.analysis.pipeline.contract.ReportSearchInputMode;
import ax.clio.analysis.pipeline.contract.ReportSearchPreparation;

import ax.clio.llm.entity.LlmConfig;
import ax.clio.llm.service.LlmConfigService;
import org.springframework.stereotype.Component;

/** rule-based/LLM 리포트 구조화 선택을 캡슐화한 {@link ReportPreparer} 구현. */
@Component
class DefaultReportPreparer implements ReportPreparer {

	private final ReportSearchPreparer reportSearchPreparer;
	private final LlmConfigService llmConfigService;

	DefaultReportPreparer(ReportSearchPreparer reportSearchPreparer, LlmConfigService llmConfigService) {
		this.reportSearchPreparer = reportSearchPreparer;
		this.llmConfigService = llmConfigService;
	}

	@Override
	public ReportSearchPreparation prepare(AnalysisJob job) {
		if (job.getSearchMode() == ReportSearchInputMode.RAW_ONLY || job.getLlmConfigId() == null) {
			return ReportSearchPreparation.rawOnly();
		}
		LlmConfig config = llmConfigService.getConfig(job.getLlmConfigId());
		return reportSearchPreparer.prepare(job.getReport(), config, job.getLlmModel());
	}
}
