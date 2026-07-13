package ax.clio.analysis.job;

import ax.clio.analysis.pipeline.AnalysisGraph;
import ax.clio.analysis.pipeline.AnalysisResultDraft;

import ax.clio.memory.IssueMemoryService;
import ax.clio.report.BugReportStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 분석 잡 실행 드라이버. 파이프라인 계산은 {@link AnalysisGraph}(langgraph4j, #12)가 맡고, 이 클래스는
 * 잡 상태 전이와 결과 영속(complete/remember)·실패 처리만 담당한다.
 */
@Component
public class AnalysisWorker {

	private final AnalysisJobRepository analysisJobRepository;
	private final AnalysisGraph analysisGraph;
	private final IssueMemoryService issueMemoryService;

	public AnalysisWorker(AnalysisJobRepository analysisJobRepository, AnalysisGraph analysisGraph,
			IssueMemoryService issueMemoryService) {
		this.analysisJobRepository = analysisJobRepository;
		this.analysisGraph = analysisGraph;
		this.issueMemoryService = issueMemoryService;
	}

	@Transactional
	public void run(Long jobId) {
		AnalysisJob job = analysisJobRepository.findById(jobId).orElseThrow();
		try {
			job.start();
			job.getReport().changeStatus(BugReportStatus.ANALYZING);

			// #12: 파이프라인을 그래프로 실행(prepare→search→flow→memory→draft→report). 실패 시 예외 전파.
			AnalysisResultDraft draft = analysisGraph.run(jobId);

			job.complete(draft);
			job.getReport().changeStatus(BugReportStatus.COMPLETED);
			// #8: 분석된 리포트를 Issue Memory에 기억(이후 리포트가 유사이슈로 찾을 수 있게).
			issueMemoryService.remember(job.getReport());
		} catch (Exception exception) {
			job.fail(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
			job.getReport().changeStatus(BugReportStatus.FAILED);
		}
	}
}
