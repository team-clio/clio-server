package ax.clio.analysis.job.service;

import ax.clio.analysis.job.dto.AnalysisJobCreateRequest;
import ax.clio.analysis.job.dto.AnalysisJobResponse;
import ax.clio.analysis.job.entity.AnalysisJob;
import ax.clio.analysis.job.repository.AnalysisJobRepository;

import ax.clio.analysis.pipeline.contract.ReportSearchInputMode;

import java.util.List;
import java.util.concurrent.Executor;

import ax.clio.common.BusinessException;
import ax.clio.llm.entity.LlmConfig;
import ax.clio.llm.service.LlmConfigService;
import ax.clio.report.entity.BugReport;
import ax.clio.report.service.BugReportService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisJobService {

	private final AnalysisJobRepository analysisJobRepository;
	private final BugReportService bugReportService;
	private final AnalysisWorker analysisWorker;
	private final Executor analysisTaskExecutor;
	private final LlmConfigService llmConfigService;

	public AnalysisJobService(AnalysisJobRepository analysisJobRepository, BugReportService bugReportService,
			AnalysisWorker analysisWorker, @Qualifier("analysisTaskExecutor") Executor analysisTaskExecutor,
			LlmConfigService llmConfigService) {
		this.analysisJobRepository = analysisJobRepository;
		this.bugReportService = bugReportService;
		this.analysisWorker = analysisWorker;
		this.analysisTaskExecutor = analysisTaskExecutor;
		this.llmConfigService = llmConfigService;
	}

	@Transactional
	public AnalysisJobResponse create(Long reportId, AnalysisJobCreateRequest request) {
		BugReport report = bugReportService.getReport(reportId);
		Long llmConfigId = request == null ? null : request.llmConfigId();
		String model = request == null ? null : request.model();
		ReportSearchInputMode searchMode = request == null ? null : request.searchMode();
		if (llmConfigId != null) {
			LlmConfig config = llmConfigService.getConfig(llmConfigId);
			if (!config.isEnabled()) {
				throw new BusinessException(HttpStatus.BAD_REQUEST, "LLM config is disabled");
			}
			if (model == null || model.isBlank()) {
				model = config.getDefaultModel();
			}
			if (searchMode == null) {
				searchMode = ReportSearchInputMode.HYBRID;
			}
		} else {
			searchMode = ReportSearchInputMode.RAW_ONLY;
		}
		AnalysisJob job = analysisJobRepository.save(new AnalysisJob(report, llmConfigId, model, searchMode));
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				analysisTaskExecutor.execute(() -> analysisWorker.run(job.getId()));
			}
		});
		return AnalysisJobResponse.from(job);
	}

	@Transactional(readOnly = true)
	public AnalysisJobResponse findById(Long id) {
		return AnalysisJobResponse.from(getJob(id));
	}

	@Transactional(readOnly = true)
	public List<AnalysisJobResponse> findByReportId(Long reportId) {
		bugReportService.getReport(reportId);
		return analysisJobRepository.findByReportIdOrderByCreatedAtDesc(reportId).stream()
				.map(AnalysisJobResponse::from)
				.toList();
	}

	private AnalysisJob getJob(Long id) {
		return analysisJobRepository.findById(id)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Analysis job not found"));
	}
}
