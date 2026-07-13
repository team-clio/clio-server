package ax.clio.memory.issue;

/**
 * 유사도 점수가 붙은 이슈 임베딩. score는 코사인 유사도(높을수록 가까움).
 * pgvector 코사인 거리 {@code <=>}는 {@code score = 1 - distance}로 변환한다.
 */
public record ScoredIssue(IssueEmbedding issue, double score) {
}
