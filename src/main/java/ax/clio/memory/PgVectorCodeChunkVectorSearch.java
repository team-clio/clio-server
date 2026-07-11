package ax.clio.memory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로덕션 벡터검색: pgvector 코사인 거리 {@code <=>}로 top-k를 뽑는다 (D4).
 *
 * <p>embedding은 이식적 {@code real[]} 컬럼으로 저장되므로(D4-1) 쿼리 시 {@code embedding::vector}로 캐스팅한다.
 * pgvector는 H2에 없어 <b>이 경로는 CI에서 실행하지 않는다</b> — 실경로 검증은 별도 벤치마크(비-CI)의 몫이다(D4).
 */
@Component
public class PgVectorCodeChunkVectorSearch implements CodeChunkVectorSearch {

	@PersistenceContext
	private EntityManager entityManager;

	private final CodeChunkRepository codeChunkRepository;

	public PgVectorCodeChunkVectorSearch(CodeChunkRepository codeChunkRepository) {
		this.codeChunkRepository = codeChunkRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ScoredCodeChunk> searchByVector(Long projectId, float[] query, int topK) {
		if (query == null || query.length == 0 || topK <= 0) {
			return List.of();
		}
		Query nativeQuery = entityManager.createNativeQuery("""
				SELECT id, (embedding::vector <=> CAST(:query AS vector)) AS distance
				FROM code_chunks
				WHERE project_id = :projectId AND embedding IS NOT NULL
				ORDER BY distance ASC
				LIMIT :topK
				""");
		nativeQuery.setParameter("query", toVectorLiteral(query));
		nativeQuery.setParameter("projectId", projectId);
		nativeQuery.setParameter("topK", topK);

		@SuppressWarnings("unchecked")
		List<Object[]> rows = nativeQuery.getResultList();
		if (rows.isEmpty()) {
			return List.of();
		}

		// distance를 id 순서대로 보존하며 chunk 로드.
		Map<Long, Double> scoreById = new LinkedHashMap<>();
		for (Object[] row : rows) {
			long id = ((Number) row[0]).longValue();
			double distance = ((Number) row[1]).doubleValue();
			scoreById.put(id, 1.0 - distance);
		}
		Map<Long, CodeChunk> chunkById = new LinkedHashMap<>();
		codeChunkRepository.findAllById(scoreById.keySet())
				.forEach(chunk -> chunkById.put(chunk.getId(), chunk));

		return scoreById.entrySet().stream()
				.filter(entry -> chunkById.containsKey(entry.getKey()))
				.map(entry -> new ScoredCodeChunk(chunkById.get(entry.getKey()), entry.getValue()))
				.toList();
	}

	/** pgvector 텍스트 리터럴 {@code [v0,v1,...]}. */
	private static String toVectorLiteral(float[] vector) {
		StringBuilder builder = new StringBuilder(vector.length * 8);
		builder.append('[');
		for (int i = 0; i < vector.length; i++) {
			if (i > 0) {
				builder.append(',');
			}
			builder.append(vector[i]);
		}
		return builder.append(']').toString();
	}
}
