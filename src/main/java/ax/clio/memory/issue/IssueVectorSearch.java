package ax.clio.memory.issue;

import ax.clio.memory.code.CodeChunkVectorSearch;

import java.util.List;

/**
 * 이슈 임베딩에 대한 벡터 유사도 top-k 검색 (I4). #7 {@link CodeChunkVectorSearch}의 병렬 구현.
 *
 * <p>구현: {@link PgVectorIssueVectorSearch}(프로덕션, CI 미실행) / {@link InMemoryIssueVectorSearch}(CI·dev).
 * 자기 자신 제외는 호출자(서비스)에서 처리한다 — 이 seam은 순수 top-k만 반환.
 */
public interface IssueVectorSearch {

	List<ScoredIssue> searchByVector(Long projectId, float[] query, int topK);
}
