package ax.clio.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import ax.clio.project.Project;
import ax.clio.report.BugReport;
import org.junit.jupiter.api.Test;

/**
 * 그래프가 포트만 조합해 파이프라인을 실행하고 결과가 보존되는지(동작 보존, P8) 검증. 각 단계 포트는 mock,
 * 점수화는 <b>실제 {@link RuleBasedScorer}</b>를 써서 rule-based 점수가 그대로 유지됨을 확인한다.
 */
class AnalysisGraphTest {

	private final AnalysisJobRepository analysisJobRepository = mock(AnalysisJobRepository.class);
	private final ReportPreparer reportPreparer = mock(ReportPreparer.class);
	private final CandidateSearcher candidateSearcher = mock(CandidateSearcher.class);
	private final FlowAnalyzer flowAnalyzer = mock(FlowAnalyzer.class);
	private final MemoryRetriever memoryRetriever = mock(MemoryRetriever.class);
	private final ReportGenerator reportGenerator = mock(ReportGenerator.class);
	private final Scorer scorer = new RuleBasedScorer();

	private final AnalysisGraph graph = new AnalysisGraph(analysisJobRepository, reportPreparer, candidateSearcher,
			flowAnalyzer, memoryRetriever, scorer, reportGenerator);

	@Test
	void runsPipelineThroughPortsAndPreservesRuleBasedScoring() {
		BugReport report = new BugReport(new Project("demo", "/w/demo", "d"), "결제 취소 오류", "결제 취소가 안 됨");
		AnalysisJob job = new AnalysisJob(report, null, null, ReportSearchInputMode.RAW_ONLY);
		when(analysisJobRepository.findById(any())).thenReturn(Optional.of(job));
		when(reportPreparer.prepare(any())).thenReturn(ReportSearchPreparation.rawOnly());
		when(candidateSearcher.search(any(), any(), any())).thenReturn(List.of(
				new RankedCodeCandidate(1L, "src/A.java", "A", null, "SERVICE", null, 10, "snip",
						false, 50, 60, 2, List.of())));
		when(flowAnalyzer.trace(any(), any())).thenReturn(List.of());
		when(memoryRetriever.retrieve(any())).thenReturn(new MemoryContext(List.of(), List.of()));
		// report 단계는 RAW_ONLY라 draft 그대로 통과
		when(reportGenerator.generate(any(), any())).thenAnswer(inv -> inv.getArgument(1));

		AnalysisResultDraft draft = graph.run(1L);

		assertThat(draft.importanceScore()).isEqualTo(50);
		assertThat(draft.difficultyScore()).isEqualTo(33); // 25 + 1*8
		assertThat(draft.riskScore()).isEqualTo(20);
		assertThat(draft.relatedCode()).hasSize(1);
		assertThat(draft.relatedCode().get(0).filePath()).isEqualTo("src/A.java");
		assertThat(draft.summary()).contains("결제 취소 오류");
	}
}
