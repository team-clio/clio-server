package ax.clio.analysis;

import java.util.List;
import java.util.concurrent.Executor;

import ax.clio.common.BusinessException;
import ax.clio.report.BugReport;
import ax.clio.report.BugReportService;
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

	public AnalysisJobService(AnalysisJobRepository analysisJobRepository, BugReportService bugReportService,
			AnalysisWorker analysisWorker, @Qualifier("analysisTaskExecutor") Executor analysisTaskExecutor) {
		this.analysisJobRepository = analysisJobRepository;
		this.bugReportService = bugReportService;
		this.analysisWorker = analysisWorker;
		this.analysisTaskExecutor = analysisTaskExecutor;
	}

	@Transactional
	public AnalysisJobResponse create(Long reportId) {
		BugReport report = bugReportService.getReport(reportId);
		AnalysisJob job = analysisJobRepository.save(new AnalysisJob(report));
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
