package ax.clio.analysis;

/**
 * LLM이 생성한 분석 리포트 텍스트 (roadmap #10 / 4.3, L2). 사람이 읽는 3필드만 담는다 — 점수·근거·검색결과는
 * rule-based가 계속 담당한다(원칙). LLM 실패 시엔 이 값 대신 rule-based 문자열로 폴백한다(L3).
 */
public record GeneratedReport(
		String summary,
		String recommendedFix,
		String recommendedTests
) {
}
