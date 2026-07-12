package ax.clio.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import ax.clio.llm.LlmClient;
import ax.clio.llm.LlmConfig;
import ax.clio.llm.LlmProvider;
import ax.clio.project.Project;
import ax.clio.report.BugReport;
import org.junit.jupiter.api.Test;

/**
 * LLM 리포트 writer (roadmap #10 / 4.3). LlmClient mock으로 정상 생성과 실패 시 폴백 신호(Optional.empty)를 검증(L7).
 */
class LlmReportWriterTest {

	private final LlmClient llmClient = mock(LlmClient.class);
	private final LlmReportWriter writer = new LlmReportWriter(llmClient);

	@Test
	void generatesReportFromValidResponse() {
		when(llmClient.completeJson(any(), any(), any(), any())).thenReturn(chatCompletion("""
				{
				  "summary": "결제 취소 흐름의 상태 갱신 누락으로 보입니다.",
				  "recommendedFix": "PaymentService의 취소 처리에서 상태 반영을 확인하세요.",
				  "recommendedTests": "결제 취소 통합 테스트를 추가하세요."
				}
				"""));

		Optional<GeneratedReport> result = writer.write(report(), draft(), config(), "gpt-test");

		assertThat(result).isPresent();
		assertThat(result.get().summary()).contains("상태 갱신 누락");
		assertThat(result.get().recommendedFix()).contains("PaymentService");
		assertThat(result.get().recommendedTests()).contains("통합 테스트");
	}

	@Test
	void fallsBackWhenRequiredFieldMissing() {
		when(llmClient.completeJson(any(), any(), any(), any())).thenReturn(chatCompletion("""
				{
				  "summary": "요약만 있고 추천 필드가 없음"
				}
				"""));

		assertThat(writer.write(report(), draft(), config(), "gpt-test")).isEmpty();
	}

	@Test
	void fallsBackWhenContentEmpty() {
		when(llmClient.completeJson(any(), any(), any(), any())).thenReturn("""
				{"choices":[{"message":{"content":""}}]}
				""");

		assertThat(writer.write(report(), draft(), config(), "gpt-test")).isEmpty();
	}

	@Test
	void fallsBackWhenClientThrows() {
		when(llmClient.completeJson(any(), any(), any(), any())).thenThrow(new RuntimeException("upstream 500"));

		assertThat(writer.write(report(), draft(), config(), "gpt-test")).isEmpty();
	}

	private String chatCompletion(String content) {
		String escaped = content
				.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n");
		return """
				{"choices":[{"message":{"content":"%s"}}]}
				""".formatted(escaped);
	}

	private BugReport report() {
		Project project = new Project("shop", "/workspace/shop", "shop service");
		return new BugReport(project, "결제 취소가 반영되지 않음", "결제 취소 후에도 주문이 결제완료로 남습니다.");
	}

	private AnalysisResultDraft draft() {
		return new AnalysisResultDraft(
				70, 55, 60, "UNKNOWN", "payment,cancel", "Payment",
				"rule-based summary",
				List.of(new RelatedCodeEntry("src/PaymentService.java", "PaymentService", "SERVICE", "EXACT", 42, "…", 90, 3)),
				List.of(), "rule-based rationale", "rule-based fix", "rule-based tests",
				List.of(), List.of());
	}

	private LlmConfig config() {
		return new LlmConfig("openai", LlmProvider.OPENAI, "https://api.openai.com/v1", "sk-test", "gpt-test", true, true);
	}
}
