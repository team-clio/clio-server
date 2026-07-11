package ax.clio.memory;

/**
 * 유사도 점수가 붙은 chunk. score는 코사인 유사도(높을수록 가까움, 대략 [-1, 1]).
 * pgvector의 코사인 거리 {@code <=>}는 {@code score = 1 - distance}로 변환한다.
 */
public record ScoredCodeChunk(CodeChunk chunk, double score) {
}
