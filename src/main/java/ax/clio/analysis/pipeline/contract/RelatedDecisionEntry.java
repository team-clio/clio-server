package ax.clio.analysis.pipeline.contract;

/**
 * 분석 결과에 붙는 "관련 설계 결정" 항목 (roadmap #9). decisionId로 결정 메모를 가리키고, score는 유사도.
 *
 * <p>D7: 충돌은 자동 판정하지 않는다. 관련 결정을 참고로 보여줘 사람이 추천↔결정 충돌을 알아채게 한다
 * (자동 충돌 판정은 LLM 근거검증 #11의 몫).
 */
public record RelatedDecisionEntry(
		Long decisionId,
		String title,
		double score
) implements java.io.Serializable {
}
