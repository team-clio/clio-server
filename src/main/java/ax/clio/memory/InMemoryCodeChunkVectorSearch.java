package ax.clio.memory;

import java.util.Comparator;
import java.util.List;

/**
 * 앱단 코사인 유사도 검색 (CI/dev 대체·레퍼런스). 프로젝트 chunk를 로드해 자바에서 top-k를 계산한다.
 * 프로덕션 벡터검색은 {@link PgVectorCodeChunkVectorSearch}(pgvector)이며, 이 구현은 pgvector 없이도
 * 파이프라인 배선을 검증·구동하기 위한 것이다(D4).
 *
 * <p>활성 빈 충돌을 피하려 {@code @Component}가 아니다 — 테스트/대체 경로에서 직접 생성해 쓴다.
 */
public class InMemoryCodeChunkVectorSearch implements CodeChunkVectorSearch {

	private final CodeChunkRepository codeChunkRepository;

	public InMemoryCodeChunkVectorSearch(CodeChunkRepository codeChunkRepository) {
		this.codeChunkRepository = codeChunkRepository;
	}

	@Override
	public List<ScoredCodeChunk> searchByVector(Long projectId, float[] query, int topK) {
		if (query == null || query.length == 0 || topK <= 0) {
			return List.of();
		}
		double queryNorm = norm(query);
		if (queryNorm == 0.0) {
			return List.of();
		}
		return codeChunkRepository.findByProjectId(projectId).stream()
				.filter(chunk -> hasComparableEmbedding(chunk, query.length))
				.map(chunk -> new ScoredCodeChunk(chunk, cosine(query, queryNorm, chunk.getEmbedding())))
				.sorted(Comparator.comparingDouble(ScoredCodeChunk::score).reversed())
				.limit(topK)
				.toList();
	}

	private static boolean hasComparableEmbedding(CodeChunk chunk, int dimensions) {
		float[] embedding = chunk.getEmbedding();
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
