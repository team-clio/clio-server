package ax.clio.memory.decision;

import ax.clio.memory.embedding.EmbeddingClient;
import ax.clio.memory.issue.IssueVectorSearch;

import java.util.List;

/**
 * 결정 메모 임베딩에 대한 벡터 유사도 top-k 검색 (D5). #8 {@link IssueVectorSearch}의 병렬 구현이다.
 *
 * <p>D5: 지금이 {@link EmbeddingClient} 위 세 번째 벡터검색(코드·이슈·결정)이지만, pgvector 네이티브
 * 쿼리에 테이블·컬럼명이 박혀 있어 제네릭/primitive 추출은 강행하지 않고 병렬 복제한다(중복이 실제로
 * 아파질 때 별도 스텝에서 추출 판단).
 *
 * <p>구현: {@link PgVectorDecisionVectorSearch}(프로덕션, CI 미실행) / {@link InMemoryDecisionVectorSearch}(CI·dev).
 */
public interface DecisionVectorSearch {

	List<ScoredDecision> searchByVector(Long projectId, float[] query, int topK);
}
