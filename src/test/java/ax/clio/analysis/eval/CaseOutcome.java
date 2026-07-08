package ax.clio.analysis.eval;

import java.util.List;
import java.util.Set;

/**
 * Per-case evaluation result: the ranking-quality metrics for a single {@link EvalCase}.
 */
public record CaseOutcome(
		String caseId,
		double reciprocalRank,
		double recallAt1,
		double recallAt5,
		double recallAt10,
		double averagePrecision) {

	/**
	 * Scores one case by comparing the ranker's output against the ground-truth set.
	 *
	 * @param caseId   identifier of the evaluated case
	 * @param ranked   ranker output, best-first, distinct project-relative file paths
	 * @param relevant ground-truth relevant file paths
	 */
	public static CaseOutcome of(String caseId, List<String> ranked, Set<String> relevant) {
		return new CaseOutcome(
				caseId,
				RankingMetrics.reciprocalRank(ranked, relevant),
				RankingMetrics.recallAtK(ranked, relevant, 1),
				RankingMetrics.recallAtK(ranked, relevant, 5),
				RankingMetrics.recallAtK(ranked, relevant, 10),
				RankingMetrics.averagePrecision(ranked, relevant));
	}
}
