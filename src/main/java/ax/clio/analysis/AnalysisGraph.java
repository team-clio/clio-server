package ax.clio.analysis;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.util.Map;

import ax.clio.report.BugReport;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.springframework.stereotype.Component;

/**
 * 분석 파이프라인 오케스트레이터 (roadmap #12). langgraph4j 6노드로 단계를 잇되, <b>각 단계 포트
 * 인터페이스에만 의존</b>한다 — concrete 구현·점수 로직을 여기 두지 않는다(포트화 원칙). 한 단계의 구현이
 * 바뀌어도 이 클래스와 다른 단계는 수정되지 않는다.
 *
 * <p>노드: prepare→search→flow→memory→draft→report. 체크포인트(MemorySaver)로 실패 단계부터 재개(threadId=jobId).
 */
@Component
public class AnalysisGraph {

	private final AnalysisJobRepository analysisJobRepository;
	private final ReportPreparer reportPreparer;
	private final CandidateSearcher candidateSearcher;
	private final FlowAnalyzer flowAnalyzer;
	private final MemoryRetriever memoryRetriever;
	private final Scorer scorer;
	private final ReportGenerator reportGenerator;

	private final MemorySaver checkpointSaver = new MemorySaver();
	private final CompiledGraph<AnalysisState> compiledGraph;

	public AnalysisGraph(AnalysisJobRepository analysisJobRepository, ReportPreparer reportPreparer,
			CandidateSearcher candidateSearcher, FlowAnalyzer flowAnalyzer, MemoryRetriever memoryRetriever,
			Scorer scorer, ReportGenerator reportGenerator) {
		this.analysisJobRepository = analysisJobRepository;
		this.reportPreparer = reportPreparer;
		this.candidateSearcher = candidateSearcher;
		this.flowAnalyzer = flowAnalyzer;
		this.memoryRetriever = memoryRetriever;
		this.scorer = scorer;
		this.reportGenerator = reportGenerator;
		this.compiledGraph = buildGraph();
	}

	/**
	 * jobId에 대해 그래프를 실행하고 최종 draft를 반환한다. threadId=jobId라 실패 후 같은 jobId로 다시 부르면
	 * 마지막 체크포인트부터 재개한다. 노드 예외는 밖으로 전파돼 호출자(워커)가 job.fail 처리한다.
	 */
	public AnalysisResultDraft run(Long jobId) {
		RunnableConfig config = RunnableConfig.builder().threadId(String.valueOf(jobId)).build();
		Map<String, Object> input = checkpointSaver.get(config).isPresent()
				? null
				: Map.of(AnalysisState.JOB_ID, jobId);
		return compiledGraph.invoke(input, config)
				.orElseThrow(() -> new IllegalStateException("Analysis graph produced no final state"))
				.draft();
	}

	private CompiledGraph<AnalysisState> buildGraph() {
		try {
			StateGraph<AnalysisState> graph = new StateGraph<>(AnalysisState.SCHEMA, AnalysisState::new)
					.addNode("prepare", node_async(this::prepareNode))
					.addNode("search", node_async(this::searchNode))
					.addNode("flow", node_async(this::flowNode))
					.addNode("memory", node_async(this::memoryNode))
					.addNode("draft", node_async(this::draftNode))
					.addNode("report", node_async(this::reportNode))
					.addEdge(START, "prepare")
					.addEdge("prepare", "search")
					.addEdge("search", "flow")
					.addEdge("flow", "memory")
					.addEdge("memory", "draft")
					.addEdge("draft", "report")
					.addEdge("report", END);
			return graph.compile(CompileConfig.builder().checkpointSaver(checkpointSaver).build());
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to build analysis graph", exception);
		}
	}

	// --- 노드: 포트만 호출하고 결과를 state에 담는다 (로직 없음) ---

	private Map<String, Object> prepareNode(AnalysisState state) {
		return Map.of(AnalysisState.PREPARATION, reportPreparer.prepare(job(state)));
	}

	private Map<String, Object> searchNode(AnalysisState state) {
		AnalysisJob job = job(state);
		return Map.of(AnalysisState.CANDIDATES,
				candidateSearcher.search(job.getReport(), state.preparation(), job.getSearchMode()));
	}

	private Map<String, Object> flowNode(AnalysisState state) {
		BugReport report = job(state).getReport();
		return Map.of(AnalysisState.FLOWS, flowAnalyzer.trace(report.getProject().getId(), state.candidates()));
	}

	private Map<String, Object> memoryNode(AnalysisState state) {
		return Map.of(AnalysisState.MEMORY, memoryRetriever.retrieve(job(state).getReport()));
	}

	private Map<String, Object> draftNode(AnalysisState state) {
		BugReport report = job(state).getReport();
		MemoryContext memory = state.memory();
		return Map.of(AnalysisState.DRAFT, scorer.score(report, state.preparation(), state.candidates(),
				state.flows(), memory.similarIssues(), memory.relatedDecisions()));
	}

	private Map<String, Object> reportNode(AnalysisState state) {
		AnalysisJob job = job(state);
		return Map.of(AnalysisState.DRAFT, reportGenerator.generate(job, state.draft()));
	}

	private AnalysisJob job(AnalysisState state) {
		return analysisJobRepository.findById(state.jobId()).orElseThrow();
	}
}
