package ax.clio.analysis.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import ax.clio.analysis.CodeCandidateRanker;
import ax.clio.analysis.ReportSearchInputBuilder;
import ax.clio.analysis.ReportSearchInputMode;
import ax.clio.code.CodeSearchMatchType;
import ax.clio.code.CodeSearchResult;
import ax.clio.code.CodeSearchService;
import ax.clio.code.CodeSymbolType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RankingEvaluatorTest {

	private CodeSearchService codeSearchService;
	private RankingEvaluator evaluator;

	@BeforeEach
	void setUp() {
		codeSearchService = mock(CodeSearchService.class);
		// Real builder + ranker so the test exercises the actual pipeline wiring.
		evaluator = new RankingEvaluator(
				new ReportSearchInputBuilder(),
				new CodeCandidateRanker(codeSearchService, mock(ax.clio.memory.CodeMemorySearchService.class)),
				RankingEvaluator.PreparationProvider.rawOnly());
	}

	@Test
	void rankingRelevantFileFirstYieldsPerfectScores() {
		// Any raw-report query surfaces the file that the fix actually touched.
		when(codeSearchService.search(eq(1L), anyString(), anyInt()))
				.thenReturn(List.of(symbol("src/main/java/PaymentService.java", "PaymentService")));

		EvalCase c = new EvalCase("sha1", "fix payment cancellation NPE", "stack trace here",
				Set.of("src/main/java/PaymentService.java"));

		CaseOutcome outcome = evaluator.evaluate(1L, c, ReportSearchInputMode.RAW_ONLY);

		assertThat(outcome.reciprocalRank()).isCloseTo(1.0, within(1e-6));
		assertThat(outcome.recallAt1()).isCloseTo(1.0, within(1e-6));
	}

	@Test
	void relevantFileBuriedBelowNoiseLowersReciprocalRank() {
		// The relevant file ranks third behind two higher-scoring but irrelevant hits.
		when(codeSearchService.search(eq(1L), anyString(), anyInt()))
				.thenReturn(List.of(
						symbol("A.java", "Alpha", 100),
						symbol("B.java", "Beta", 90),
						symbol("Target.java", "Target", 80)));

		EvalCase c = new EvalCase("sha2", "fix something", "", Set.of("Target.java"));

		CaseOutcome outcome = evaluator.evaluate(1L, c, ReportSearchInputMode.RAW_ONLY);

		// Target is at rank 3 → RR = 1/3, Recall@1 = 0, Recall@5 = 1.
		assertThat(outcome.reciprocalRank()).isCloseTo(1.0 / 3, within(1e-6));
		assertThat(outcome.recallAt1()).isEqualTo(0.0);
		assertThat(outcome.recallAt5()).isCloseTo(1.0, within(1e-6));
	}

	@Test
	void evaluateAllAggregatesAcrossCases() {
		when(codeSearchService.search(eq(1L), anyString(), anyInt()))
				.thenReturn(List.of(symbol("Hit.java", "Hit")));

		List<EvalCase> cases = List.of(
				new EvalCase("hit", "found", "", Set.of("Hit.java")),
				new EvalCase("miss", "nothere", "", Set.of("Missing.java")));

		EvalScorecard card = evaluator.evaluateAll(1L, cases, ReportSearchInputMode.RAW_ONLY);

		assertThat(card.caseCount()).isEqualTo(2);
		assertThat(card.mrr()).isCloseTo(0.5, within(1e-6));
	}

	private CodeSearchResult symbol(String filePath, String name) {
		return symbol(filePath, name, 100);
	}

	private CodeSearchResult symbol(String filePath, String name, int score) {
		return new CodeSearchResult(1L, filePath, name, CodeSymbolType.CLASS, "SERVICE",
				CodeSearchMatchType.CLASS_NAME, 1, name, score, false);
	}
}
