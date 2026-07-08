package ax.clio.analysis.eval;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure ranking-quality metrics for bug-localization evaluation.
 *
 * <p>Each method takes the ranker's output ({@code ranked}, best-first, distinct file
 * paths) and the ground-truth relevant set, and returns a score in [0, 1].
 */
final class RankingMetrics {

	private RankingMetrics() {
	}

	/** 1 / (1-indexed rank of the first relevant item); 0 if none appears. */
	static double reciprocalRank(List<String> ranked, Set<String> relevant) {
		for (int i = 0; i < ranked.size(); i++) {
			if (relevant.contains(ranked.get(i))) {
				return 1.0 / (i + 1);
			}
		}
		return 0.0;
	}

	/** Fraction of relevant items that appear within the top {@code k}; 0 if relevant is empty. */
	static double recallAtK(List<String> ranked, Set<String> relevant, int k) {
		if (relevant.isEmpty()) {
			return 0.0;
		}
		Set<String> topK = new HashSet<>(ranked.subList(0, Math.min(k, ranked.size())));
		topK.retainAll(relevant);
		return (double) topK.size() / relevant.size();
	}

	/** Average precision over the full ranked list; 0 if relevant is empty. */
	static double averagePrecision(List<String> ranked, Set<String> relevant) {
		if (relevant.isEmpty()) {
			return 0.0;
		}
		Set<String> seen = new HashSet<>();
		int hits = 0;
		double sum = 0.0;
		for (int i = 0; i < ranked.size(); i++) {
			String path = ranked.get(i);
			if (relevant.contains(path) && seen.add(path)) {
				hits++;
				sum += (double) hits / (i + 1);
			}
		}
		return sum / relevant.size();
	}
}
