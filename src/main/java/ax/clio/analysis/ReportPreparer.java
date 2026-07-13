package ax.clio.analysis;

/**
 * [파이프라인 포트] 리포트 구조화 단계. 잡의 검색 모드·LLM 설정에 따라 rule-based/LLM 구현을 고른다.
 * 구현이 바뀌어도 그래프·다른 단계는 이 인터페이스에만 의존한다.
 */
public interface ReportPreparer {

	ReportSearchPreparation prepare(AnalysisJob job);
}
