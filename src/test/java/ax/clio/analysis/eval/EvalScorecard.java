package ax.clio.analysis.eval;

import java.util.List;

/**
 * Aggregate ranking quality over an evaluation run.
 *
 * @param caseCount            number of cases evaluated
 * @param mrr                  Mean Reciprocal Rank
 * @param recallAt1            mean Recall@1
 * @param recallAt5            mean Recall@5
 * @param recallAt10           mean Recall@10
 * @param meanAveragePrecision MAP
 */
public record EvalScorecard(
		int caseCount,
		double mrr,
		double recallAt1,
		double recallAt5,
		double recallAt10,
		double meanAveragePrecision) {

	/** Aggregates per-case outcomes into a single scorecard (simple arithmetic mean). */
	public static EvalScorecard aggregate(List<CaseOutcome> outcomes) {
		if (outcomes.isEmpty()) {
			return new EvalScorecard(0, 0, 0, 0, 0, 0);
		}
		int n = outcomes.size();
		return new EvalScorecard(
				n,
				mean(outcomes, CaseOutcome::reciprocalRank),
				mean(outcomes, CaseOutcome::recallAt1),
				mean(outcomes, CaseOutcome::recallAt5),
				mean(outcomes, CaseOutcome::recallAt10),
				mean(outcomes, CaseOutcome::averagePrecision));
	}

	private static double mean(List<CaseOutcome> outcomes, java.util.function.ToDoubleFunction<CaseOutcome> field) {
		return outcomes.stream().mapToDouble(field).average().orElse(0.0);
	}

	/** Human-readable one-block summary for the runner to print. */
	public String toReport() {
		return """
				Ranking evaluation (%d cases)
				  MRR         : %.3f
				  Recall@1    : %.3f
				  Recall@5    : %.3f
				  Recall@10   : %.3f
				  MAP         : %.3f""".formatted(
				caseCount, mrr, recallAt1, recallAt5, recallAt10, meanAveragePrecision);
	}
}
