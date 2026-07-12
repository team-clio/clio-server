package ax.clio.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import ax.clio.llm.LlmConfigService;
import ax.clio.memory.DecisionMemoryService;
import ax.clio.memory.IssueMemoryService;
import ax.clio.project.Project;
import ax.clio.report.BugReport;
import org.junit.jupiter.api.Test;

/**
 * 그래프가 기존 파이프라인과 동일하게 동작하는지(동작 보존, G7) 검증. 서비스를 mock하고 RAW_ONLY 잡으로
 * 실행해 prepare→search→flow→memory→draft→report 노드가 순서대로 돌고 rule-based 점수가 보존됨을 확인한다.
 */
class AnalysisGraphTest {

	private final AnalysisJobRepository analysisJobRepository = mock(AnalysisJobRepository.class);
	private final CodeCandidateRanker codeCandidateRanker = mock(CodeCandidateRanker.class);
	private final FlowTracer flowTracer = mock(FlowTracer.class);
	private final ReportSearchInputBuilder searchInputBuilder = mock(ReportSearchInputBuilder.class);
	private final ReportSearchPreparer reportSearchPreparer = mock(ReportSearchPreparer.class);
	private final LlmConfigService llmConfigService = mock(LlmConfigService.class);
	private final IssueMemoryService issueMemoryService = mock(IssueMemoryService.class);
	private final DecisionMemoryService decisionMemoryService = mock(DecisionMemoryService.class);
	private final LlmReportWriter llmReportWriter = mock(LlmReportWriter.class);
	private final ReportEvidenceVerifier reportEvidenceVerifier = mock(ReportEvidenceVerifier.class);

	private final AnalysisGraph graph = new AnalysisGraph(analysisJobRepository, codeCandidateRanker, flowTracer,
			searchInputBuilder, reportSearchPreparer, llmConfigService, issueMemoryService, decisionMemoryService,
			llmReportWriter, reportEvidenceVerifier);

	@Test
	void runsPipelineAsGraphAndPreservesRuleBasedScoring() {
		BugReport report = new BugReport(new Project("demo", "/w/demo", "d"), "결제 취소 오류", "결제 취소가 안 됨");
		AnalysisJob job = new AnalysisJob(report, null, null, ReportSearchInputMode.RAW_ONLY);
		when(analysisJobRepository.findById(any())).thenReturn(Optional.of(job));
		when(searchInputBuilder.build(any(), any(), any())).thenReturn(List.of());
		when(codeCandidateRanker.rank(any(), any())).thenReturn(List.of(
				new RankedCodeCandidate(1L, "src/A.java", "A", null, "SERVICE", null, 10, "snip",
						false, 50, 60, 2, List.of())));
		when(flowTracer.trace(any(), any())).thenReturn(List.of());
		when(issueMemoryService.findSimilar(any(), anyInt())).thenReturn(List.of());
		when(decisionMemoryService.findRelevant(any(), anyInt())).thenReturn(List.of());

		AnalysisResultDraft draft = graph.run(1L);

		// rule-based 점수 보존: candidate 1개(파일 1) + flows 0
		assertThat(draft.importanceScore()).isEqualTo(50);
		assertThat(draft.difficultyScore()).isEqualTo(33); // 25 + 1*8
		assertThat(draft.riskScore()).isEqualTo(20);       // 20 + 0
		assertThat(draft.relatedCode()).hasSize(1);
		assertThat(draft.relatedCode().get(0).filePath()).isEqualTo("src/A.java");
		assertThat(draft.summary()).contains("결제 취소 오류");
		// RAW_ONLY(llmConfigId=null): LLM 보강·근거검증 미실행 → 경고 없음
		assertThat(draft.evidenceWarnings()).isEmpty();
		assertThat(draft.similarIssues()).isEmpty();
		assertThat(draft.relatedDecisions()).isEmpty();
	}
}
