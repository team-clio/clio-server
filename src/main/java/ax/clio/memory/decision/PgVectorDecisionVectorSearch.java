package ax.clio.memory.decision;

import ax.clio.memory.issue.PgVectorIssueVectorSearch;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로덕션 결정 벡터검색: pgvector 코사인 거리 {@code <=>} top-k (D5/D8). {@code embedding}은 이식적
 * {@code real[]} 컬럼이라 쿼리 시 {@code ::vector}로 캐스팅한다. pgvector는 H2에 없어 <b>CI 미실행</b>이며
 * 실경로 검증은 별도 벤치마크의 몫이다. #8 {@link PgVectorIssueVectorSearch}의 병렬 구현.
 */
@Component
public class PgVectorDecisionVectorSearch implements DecisionVectorSearch {

	@PersistenceContext
	private EntityManager entityManager;

	private final DecisionMemoryRepository decisionMemoryRepository;

	public PgVectorDecisionVectorSearch(DecisionMemoryRepository decisionMemoryRepository) {
		this.decisionMemoryRepository = decisionMemoryRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ScoredDecision> searchByVector(Long projectId, float[] query, int topK) {
		if (query == null || query.length == 0 || topK <= 0) {
			return List.of();
		}
		Query nativeQuery = entityManager.createNativeQuery("""
				SELECT id, (embedding::vector <=> CAST(:query AS vector)) AS distance
				FROM decision_memories
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

		Map<Long, Double> scoreById = new LinkedHashMap<>();
		for (Object[] row : rows) {
			long id = ((Number) row[0]).longValue();
			double distance = ((Number) row[1]).doubleValue();
			scoreById.put(id, 1.0 - distance);
		}
		Map<Long, DecisionMemory> decisionById = new LinkedHashMap<>();
		decisionMemoryRepository.findAllById(scoreById.keySet())
				.forEach(decision -> decisionById.put(decision.getId(), decision));

		return scoreById.entrySet().stream()
				.filter(entry -> decisionById.containsKey(entry.getKey()))
				.map(entry -> new ScoredDecision(decisionById.get(entry.getKey()), entry.getValue()))
				.toList();
	}

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
