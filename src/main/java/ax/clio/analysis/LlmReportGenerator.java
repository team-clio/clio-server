package ax.clio.analysis;

import java.util.List;

import ax.clio.llm.LlmConfig;
import ax.clio.llm.LlmConfigService;
import org.springframework.stereotype.Component;

/** #10 LLM 리포트 보강 + #11 근거 검증을 결합한 {@link ReportGenerator} 구현. 실패·미설정 시 입력 draft 유지. */
@Component
class LlmReportGenerator implements ReportGenerator {

	private final LlmConfigService llmConfigService;
	private final LlmReportWriter llmReportWriter;
	private final ReportEvidenceVerifier reportEvidenceVerifier;

	LlmReportGenerator(LlmConfigService llmConfigService, LlmReportWriter llmReportWriter,
			ReportEvidenceVerifier reportEvidenceVerifier) {
		this.llmConfigService = llmConfigService;
		this.llmReportWriter = llmReportWriter;
		this.reportEvidenceVerifier = reportEvidenceVerifier;
	}

	@Override
	public AnalysisResultDraft generate(AnalysisJob job, AnalysisResultDraft draft) {
		if (job.getLlmConfigId() == null) {
			return draft;
		}
		LlmConfig config = llmConfigService.getConfig(job.getLlmConfigId());
		return llmReportWriter.write(job.getReport(), draft, config, job.getLlmModel())
				.map(generated -> verify(draft.withGeneratedReport(generated), generated))
				.orElse(draft);
	}

	private AnalysisResultDraft verify(AnalysisResultDraft enriched, GeneratedReport generated) {
		List<String> warnings = reportEvidenceVerifier.findUnsupportedReferences(
				generated, enriched.relatedCode(), enriched.flows());
		return warnings.isEmpty() ? enriched : enriched.withEvidenceWarnings(warnings);
	}
}
