package ax.clio.analysis.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class RankingMetricsTest {

	private static final double EPS = 1e-6;

	@Test
	void reciprocalRankIsInverseOfFirstRelevantPosition() {
		// relevant B first appears at position 3 (1-indexed) → 1/3
		assertThat(RankingMetrics.reciprocalRank(List.of("A", "C", "B", "D"), Set.of("B")))
				.isCloseTo(1.0 / 3, within(EPS));
	}

	@Test
	void reciprocalRankIsZeroWhenNoRelevantAppears() {
		assertThat(RankingMetrics.reciprocalRank(List.of("A", "B"), Set.of("Z")))
				.isEqualTo(0.0);
	}

	@Test
	void recallAtKCountsRelevantWithinTopK() {
		List<String> ranked = List.of("A", "C", "B", "D");
		Set<String> relevant = Set.of("A", "B");
		// top 2 = {A, C} → only A is relevant → 1/2
		assertThat(RankingMetrics.recallAtK(ranked, relevant, 2)).isCloseTo(0.5, within(EPS));
		// top 3 = {A, C, B} → both relevant → 2/2
		assertThat(RankingMetrics.recallAtK(ranked, relevant, 3)).isCloseTo(1.0, within(EPS));
	}

	@Test
	void recallAtKHandlesKLargerThanList() {
		assertThat(RankingMetrics.recallAtK(List.of("A"), Set.of("A"), 10))
				.isCloseTo(1.0, within(EPS));
	}

	@Test
	void averagePrecisionAccumulatesPrecisionAtEachHit() {
		// relevant {A, B}, ranked [A, C, B]:
		// hit at pos 1 → precision 1/1, hit at pos 3 → precision 2/3 → (1 + 0.6667)/2
		double expected = (1.0 + 2.0 / 3) / 2;
		assertThat(RankingMetrics.averagePrecision(List.of("A", "C", "B"), Set.of("A", "B")))
				.isCloseTo(expected, within(EPS));
	}

	@Test
	void emptyRelevantSetYieldsZero() {
		Set<String> none = Set.of();
		assertThat(RankingMetrics.recallAtK(List.of("A"), none, 5)).isEqualTo(0.0);
		assertThat(RankingMetrics.averagePrecision(List.of("A"), none)).isEqualTo(0.0);
	}

	@Test
	void scorecardAveragesAcrossCases() {
		CaseOutcome perfect = CaseOutcome.of("c1", List.of("A", "B"), Set.of("A"));
		CaseOutcome miss = CaseOutcome.of("c2", List.of("X", "Y"), Set.of("A"));

		EvalScorecard card = EvalScorecard.aggregate(List.of(perfect, miss));

		assertThat(card.caseCount()).isEqualTo(2);
		// perfect: RR=1, miss: RR=0 → MRR 0.5
		assertThat(card.mrr()).isCloseTo(0.5, within(EPS));
		// perfect: Recall@1=1, miss: Recall@1=0 → 0.5
		assertThat(card.recallAt1()).isCloseTo(0.5, within(EPS));
	}

	@Test
	void aggregateOfEmptyIsAllZero() {
		EvalScorecard card = EvalScorecard.aggregate(List.of());
		assertThat(card.caseCount()).isZero();
		assertThat(card.mrr()).isZero();
	}
}
