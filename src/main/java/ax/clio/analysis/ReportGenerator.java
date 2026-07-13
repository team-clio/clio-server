package ax.clio.analysis;

/**
 * [파이프라인 포트] 분석 리포트 생성 단계(#10 LLM 리포트 + #11 근거 검증). llmConfigId 없거나 실패 시
 * 입력 draft를 그대로 반환(자동 폴백).
 */
public interface ReportGenerator {

	AnalysisResultDraft generate(AnalysisJob job, AnalysisResultDraft draft);
}
