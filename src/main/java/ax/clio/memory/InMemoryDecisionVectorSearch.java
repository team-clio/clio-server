package ax.clio.memory;

import java.util.Comparator;
import java.util.List;

/**
 * 앱단 코사인 결정 검색 (CI/dev 대체·레퍼런스). 프로덕션은 {@link PgVectorDecisionVectorSearch}(pgvector)이며,
 * 이 구현은 pgvector 없이 배선을 검증·구동하기 위한 것이다(D8). 활성 빈 충돌을 피하려 {@code @Component}가 아니다.
 * #8 {@link InMemoryIssueVectorSearch}의 병렬 구현.
 */
public class InMemoryDecisionVectorSearch implements DecisionVectorSearch {

	private final DecisionMemoryRepository decisionMemoryRepository;

	public InMemoryDecisionVectorSearch(DecisionMemoryRepository decisionMemoryRepository) {
		this.decisionMemoryRepository = decisionMemoryRepository;
	}

	@Override
	public List<ScoredDecision> searchByVector(Long projectId, float[] query, int topK) {
		if (query == null || query.length == 0 || topK <= 0) {
			return List.of();
		}
		double queryNorm = norm(query);
		if (queryNorm == 0.0) {
			return List.of();
		}
		return decisionMemoryRepository.findByProjectId(projectId).stream()
				.filter(decision -> hasComparableEmbedding(decision, query.length))
				.map(decision -> new ScoredDecision(decision, cosine(query, queryNorm, decision.getEmbedding())))
				.sorted(Comparator.comparingDouble(ScoredDecision::score).reversed())
				.limit(topK)
				.toList();
	}

	private static boolean hasComparableEmbedding(DecisionMemory decision, int dimensions) {
		float[] embedding = decision.getEmbedding();
		return embedding != null && embedding.length == dimensions;
	}

	private static double cosine(float[] query, double queryNorm, float[] target) {
		double targetNorm = norm(target);
		if (targetNorm == 0.0) {
			return 0.0;
		}
		double dot = 0.0;
		for (int i = 0; i < query.length; i++) {
			dot += (double) query[i] * target[i];
		}
		return dot / (queryNorm * targetNorm);
	}

	private static double norm(float[] vector) {
		double sum = 0.0;
		for (float value : vector) {
			sum += (double) value * value;
		}
		return Math.sqrt(sum);
	}
}
