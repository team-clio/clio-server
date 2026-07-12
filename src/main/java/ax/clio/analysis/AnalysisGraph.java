package ax.clio.analysis;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ax.clio.llm.LlmConfig;
import ax.clio.llm.LlmConfigService;
import ax.clio.memory.DecisionMemoryService;
import ax.clio.memory.IssueMemoryService;
import ax.clio.report.BugReport;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.springframework.stereotype.Component;

/**
 * 분석 파이프라인을 langgraph4j 그래프로 실행한다 (roadmap #12). 기존 절차적 단계를 노드로 감싼 것으로
 * <b>동작은 동일</b>하다(리포트→draft 순수 계산). rule-based 제거·순환/에이전트화는 이 골격 위에서 이후 단계.
 *
 * <p>노드(G4): prepare → search → flow → memory → draft → report. 각 노드는 기존 서비스를 호출하고 산출물을
 * state에 축적한다. 체크포인트(G3, MemorySaver)로 단계별 상태가 저장돼 실패 단계부터 재개할 수 있다
 * (threadId=jobId, G6). JPA 엔티티는 state에 담지 않고 jobId로 조회한다(G2).
 */
@Component
public class AnalysisGraph {

	private static final int SIMILAR_ISSUE_LIMIT = 3;
	private static final int RELATED_DECISION_LIMIT = 3;

	private final AnalysisJobRepository analysisJobRepository;
	private final CodeCandidateRanker codeCandidateRanker;
	private final FlowTracer flowTracer;
	private final ReportSearchInputBuilder searchInputBuilder;
	private final ReportSearchPreparer reportSearchPreparer;
	private final LlmConfigService llmConfigService;
	private final IssueMemoryService issueMemoryService;
	private final DecisionMemoryService decisionMemoryService;
	private final LlmReportWriter llmReportWriter;
	private final ReportEvidenceVerifier reportEvidenceVerifier;

	private final MemorySaver checkpointSaver = new MemorySaver();
	private final CompiledGraph<AnalysisState> compiledGraph;

	public AnalysisGraph(AnalysisJobRepository analysisJobRepository, CodeCandidateRanker codeCandidateRanker,
			FlowTracer flowTracer, ReportSearchInputBuilder searchInputBuilder, ReportSearchPreparer reportSearchPreparer,
			LlmConfigService llmConfigService, IssueMemoryService issueMemoryService,
			DecisionMemoryService decisionMemoryService, LlmReportWriter llmReportWriter,
			ReportEvidenceVerifier reportEvidenceVerifier) {
		this.analysisJobRepository = analysisJobRepository;
		this.codeCandidateRanker = codeCandidateRanker;
		this.flowTracer = flowTracer;
		this.searchInputBuilder = searchInputBuilder;
		this.reportSearchPreparer = reportSearchPreparer;
		this.llmConfigService = llmConfigService;
		this.issueMemoryService = issueMemoryService;
		this.decisionMemoryService = decisionMemoryService;
		this.llmReportWriter = llmReportWriter;
		this.reportEvidenceVerifier = reportEvidenceVerifier;
		this.compiledGraph = buildGraph();
	}

	/**
	 * jobId에 대해 그래프를 실행하고 최종 draft를 반환한다. threadId=jobId라 실패 후 같은 jobId로 다시 부르면
	 * 마지막 체크포인트부터 재개한다(G6). 노드 예외는 밖으로 전파돼 호출자(워커)가 job.fail 처리한다.
	 */
	public AnalysisResultDraft run(Long jobId) {
		RunnableConfig config = RunnableConfig.builder().threadId(String.valueOf(jobId)).build();
		// G6: 같은 jobId의 체크포인트가 있으면(이전 실행이 중간에 실패) 입력 null로 마지막 단계부터 재개하고,
		// 없으면 jobId를 실어 처음부터 실행한다.
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

	// --- 노드: 각 단계를 기존 서비스 호출로 감싼다 (동작 동일) ---

	private Map<String, Object> prepareNode(AnalysisState state) {
		AnalysisJob job = job(state);
		return Map.of(AnalysisState.PREPARATION, prepare(job.getReport(), job));
	}

	private Map<String, Object> searchNode(AnalysisState state) {
		AnalysisJob job = job(state);
		BugReport report = job.getReport();
		List<ReportSearchInput> searchInputs = searchInputBuilder.build(report, state.preparation(), job.getSearchMode());
		List<RankedCodeCandidate> candidates = codeCandidateRanker.rank(report.getProject().getId(), searchInputs);
		return Map.of(AnalysisState.CANDIDATES, candidates);
	}

	private Map<String, Object> flowNode(AnalysisState state) {
		BugReport report = job(state).getReport();
		List<String> candidatePaths = state.candidates().stream()
				.map(RankedCodeCandidate::filePath).distinct().toList();
		List<CodeFlow> flows = flowTracer.trace(report.getProject().getId(), candidatePaths);
		return Map.of(AnalysisState.FLOWS, flows);
	}

	private Map<String, Object> memoryNode(AnalysisState state) {
		BugReport report = job(state).getReport();
		// #8 유사 과거 이슈 + #9 관련 설계 결정 (둘 다 리포트 임베딩 조회라 한 노드).
		// 엔티티를 감싼 Scored*는 체크포인트 직렬화가 안 되므로 여기서 바로 plain entry로 매핑해 담는다(G2).
		List<SimilarIssueEntry> similarIssues = issueMemoryService.findSimilar(report, SIMILAR_ISSUE_LIMIT).stream()
				.map(scored -> new SimilarIssueEntry(
						scored.issue().getReport().getId(), scored.issue().getTitleSnapshot(), scored.score()))
				.toList();
		List<RelatedDecisionEntry> relatedDecisions = decisionMemoryService.findRelevant(report, RELATED_DECISION_LIMIT)
				.stream()
				.map(scored -> new RelatedDecisionEntry(
						scored.decision().getId(), scored.decision().getTitle(), scored.score()))
				.toList();
		return Map.of(
				AnalysisState.SIMILAR_ISSUES, similarIssues,
				AnalysisState.RELATED_DECISIONS, relatedDecisions);
	}

	private Map<String, Object> draftNode(AnalysisState state) {
		BugReport report = job(state).getReport();
		AnalysisResultDraft draft = buildDraft(report, state.preparation(), state.candidates(), state.flows(),
				state.similarIssues(), state.relatedDecisions());
		return Map.of(AnalysisState.DRAFT, draft);
	}

	private Map<String, Object> reportNode(AnalysisState state) {
		AnalysisJob job = job(state);
		// #10 LLM 리포트 보강 + #11 근거 검증 (실패/미설정이면 rule-based 유지).
		AnalysisResultDraft draft = enrichWithLlmReport(job.getReport(), state.draft(), job);
		return Map.of(AnalysisState.DRAFT, draft);
	}

	private AnalysisJob job(AnalysisState state) {
		return analysisJobRepository.findById(state.jobId()).orElseThrow();
	}

	// --- 단계 로직 (AnalysisWorker에서 이전, 동작 동일) ---

	private ReportSearchPreparation prepare(BugReport report, AnalysisJob job) {
		if (job.getSearchMode() == ReportSearchInputMode.RAW_ONLY || job.getLlmConfigId() == null) {
			return ReportSearchPreparation.rawOnly();
		}
		LlmConfig config = llmConfigService.getConfig(job.getLlmConfigId());
		return reportSearchPreparer.prepare(report, config, job.getLlmModel());
	}

	/**
	 * #10: llmConfigId가 있으면 LLM 리포트로 3필드(summary·fix·tests)를 교체한다. 실패·미설정 시 rule-based
	 * draft를 그대로 반환한다(L3 자동 폴백). 점수·근거·검색결과는 건드리지 않는다.
	 */
	private AnalysisResultDraft enrichWithLlmReport(BugReport report, AnalysisResultDraft draft, AnalysisJob job) {
		if (job.getLlmConfigId() == null) {
			return draft;
		}
		LlmConfig config = llmConfigService.getConfig(job.getLlmConfigId());
		return llmReportWriter.write(report, draft, config, job.getLlmModel())
				.map(generated -> verifyEvidence(draft.withGeneratedReport(generated), generated))
				.orElse(draft);
	}

	/**
	 * #11: LLM 리포트가 근거(relatedCode·flows)에 없는 파일/클래스를 언급하면 경고를 붙인다(E3=경고만, E4).
	 * 텍스트는 유지하고 evidenceWarnings만 채운다. rule-based 폴백 경로엔 경고가 없다(빈 리스트).
	 */
	private AnalysisResultDraft verifyEvidence(AnalysisResultDraft enriched, GeneratedReport generated) {
		List<String> warnings = reportEvidenceVerifier.findUnsupportedReferences(
				generated, enriched.relatedCode(), enriched.flows());
		return warnings.isEmpty() ? enriched : enriched.withEvidenceWarnings(warnings);
	}

	private AnalysisResultDraft buildDraft(BugReport report, ReportSearchPreparation preparation,
			List<RankedCodeCandidate> candidates, List<CodeFlow> flows, List<SimilarIssueEntry> similarIssueEntries,
			List<RelatedDecisionEntry> relatedDecisionEntries) {
		long relatedFileCount = candidates.stream().map(RankedCodeCandidate::filePath).distinct().count();
		boolean touchesEntity = candidates.stream()
				.anyMatch(c -> "ENTITY".equals(c.symbolRole()));
		boolean touchesRepository = candidates.stream()
				.anyMatch(c -> "REPOSITORY".equals(c.symbolRole()));

		// 흐름이 걸친 서로 다른 레이어 수(넓게 걸칠수록 수정 범위가 크다)
		int maxFlowLayerSpan = flows.stream()
				.mapToInt(AnalysisGraph::layerSpan)
				.max()
				.orElse(0);

		int importance = scoreImportance(preparation);
		int difficulty = clamp(25 + (int) relatedFileCount * 8
				+ preparation.candidateDomains().size() * 10
				+ maxFlowLayerSpan * 5);
		int risk = clamp(20 + preparation.candidateDomains().size() * 12
				+ (touchesEntity ? 20 : 0)
				+ (touchesRepository ? 15 : 0));

		List<RelatedCodeEntry> entries = candidates.stream()
				.map(c -> new RelatedCodeEntry(
						c.filePath(), c.symbolName(), c.symbolRole(),
						c.matchType() != null ? c.matchType().name() : null,
						c.lineNumber(), c.snippet(), c.adjustedScore(), c.hitCount()))
				.toList();

		String rationale = String.join("\n",
				"- 관련 파일 수: " + relatedFileCount,
				"- 후보 도메인: " + emptyAsDash(preparation.candidateDomains()),
				"- 검색어: " + emptyAsDash(preparation.codeSearchTerms()),
				"- 검색 입력 신뢰도: " + preparation.confidence(),
				"- Repository 관련 코드 포함: " + (touchesRepository ? "예" : "아니오"),
				"- Entity 변경 가능성 신호: " + (touchesEntity ? "예" : "아니오"),
				"- 영향 흐름: " + describeFlows(flows)
		);

		String summary = "리포트 '" + report.getTitle() + "'는 " + reportType(preparation)
				+ " 입력으로 해석되며, " + relatedFileCount + "개 파일 후보와 연결되었습니다.";

		String recommendedFix = "상위 점수 관련 코드부터 재현 경로를 확인하고, Controller-Service-Repository 흐름에서 상태 변경 또는 예외 처리 누락을 우선 검토하세요.";
		String recommendedTests = "서비스 단위 테스트와 주요 흐름 통합 테스트를 추가하세요.";

		return new AnalysisResultDraft(
				importance,
				difficulty,
				risk,
				"UNKNOWN",
				String.join(",", searchTerms(preparation)),
				String.join(",", preparation.candidateDomains()),
				summary,
				entries,
				flows,
				rationale,
				recommendedFix,
				recommendedTests,
				similarIssueEntries,
				relatedDecisionEntries,
				List.of()
		);
	}

	private static int layerSpan(CodeFlow flow) {
		long distinctLayers = flow.nodes().stream()
				.map(FlowNode::role)
				.map(CodeDependencyGraph::layerOf)
				.filter(layer -> layer != CodeDependencyGraph.UNKNOWN_LAYER)
				.distinct()
				.count();
		return (int) distinctLayers;
	}

	private String describeFlows(List<CodeFlow> flows) {
		if (flows.isEmpty()) {
			return "-";
		}
		return flows.stream().map(CodeFlow::describe).collect(Collectors.joining(" | "));
	}

	private int scoreImportance(ReportSearchPreparation preparation) {
		int score = 50;
		for (String symptom : preparation.symptoms()) {
			if (symptom.equals("FAILED") || symptom.equals("ERROR") || symptom.equals("TIMEOUT")) {
				score += 15;
			}
			if (symptom.equals("UNAUTHORIZED") || symptom.equals("STATE_NOT_UPDATED")) {
				score += 10;
			}
		}
		for (String domain : preparation.candidateDomains()) {
			String normalized = domain.toLowerCase();
			if (normalized.contains("payment") || normalized.contains("auth") || normalized.contains("order")) {
				score += 10;
			}
		}
		return clamp(score);
	}

	private List<String> searchTerms(ReportSearchPreparation preparation) {
		return java.util.stream.Stream.concat(preparation.businessTerms().stream(), preparation.codeSearchTerms().stream())
				.distinct()
				.toList();
	}

	private String reportType(ReportSearchPreparation preparation) {
		return preparation.reportType() == null || preparation.reportType().isBlank() ? "UNKNOWN" : preparation.reportType();
	}

	private int clamp(int score) {
		return Math.max(0, Math.min(100, score));
	}

	private String emptyAsDash(List<String> values) {
		return values.isEmpty() ? "-" : String.join(",", values);
	}
}
