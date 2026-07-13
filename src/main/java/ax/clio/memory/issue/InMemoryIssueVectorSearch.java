package ax.clio.memory.issue;

import java.util.Comparator;
import java.util.List;

/**
 * 앱단 코사인 이슈 검색 (CI/dev 대체·레퍼런스). 프로덕션은 {@link PgVectorIssueVectorSearch}(pgvector)이며,
 * 이 구현은 pgvector 없이 배선을 검증·구동하기 위한 것이다(I7). 활성 빈 충돌을 피하려 {@code @Component}가 아니다.
 */
public class InMemoryIssueVectorSearch implements IssueVectorSearch {

	private final IssueEmbeddingRepository issueEmbeddingRepository;

	public InMemoryIssueVectorSearch(IssueEmbeddingRepository issueEmbeddingRepository) {
		this.issueEmbeddingRepository = issueEmbeddingRepository;
	}

	@Override
	public List<ScoredIssue> searchByVector(Long projectId, float[] query, int topK) {
		if (query == null || query.length == 0 || topK <= 0) {
			return List.of();
		}
		double queryNorm = norm(query);
		if (queryNorm == 0.0) {
			return List.of();
		}
		return issueEmbeddingRepository.findByProjectId(projectId).stream()
				.filter(issue -> hasComparableEmbedding(issue, query.length))
				.map(issue -> new ScoredIssue(issue, cosine(query, queryNorm, issue.getEmbedding())))
				.sorted(Comparator.comparingDouble(ScoredIssue::score).reversed())
				.limit(topK)
				.toList();
	}

	private static boolean hasComparableEmbedding(IssueEmbedding issue, int dimensions) {
		float[] embedding = issue.getEmbedding();
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
