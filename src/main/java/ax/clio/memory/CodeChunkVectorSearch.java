package ax.clio.memory;

import java.util.List;

/**
 * chunk embedding에 대한 벡터 유사도 top-k 검색 seam (D4).
 *
 * <p>구현:
 * <ul>
 *   <li>{@link PgVectorCodeChunkVectorSearch} — 프로덕션. pgvector 네이티브 쿼리. CI 미실행(D4: 별도 벤치마크).</li>
 *   <li>{@link InMemoryCodeChunkVectorSearch} — 앱단 코사인. CI/dev 대체·레퍼런스.</li>
 * </ul>
 */
public interface CodeChunkVectorSearch {

	/** 쿼리 벡터와 코사인 유사도가 높은 순으로 최대 topK개 chunk를 돌려준다. */
	List<ScoredCodeChunk> searchByVector(Long projectId, float[] query, int topK);
}
