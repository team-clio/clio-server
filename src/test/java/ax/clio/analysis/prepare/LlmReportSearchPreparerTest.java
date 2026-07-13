package ax.clio.analysis.prepare;

import ax.clio.analysis.pipeline.ReportSearchPreparation;
import ax.clio.analysis.prepare.CodebaseDomainCandidateProvider;
import ax.clio.analysis.prepare.LlmReportSearchPreparer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import ax.clio.common.BusinessException;
import ax.clio.llm.LlmClient;
import ax.clio.llm.LlmConfig;
import ax.clio.llm.LlmProvider;
import ax.clio.project.Project;
import ax.clio.report.BugReport;
import org.junit.jupiter.api.Test;

class LlmReportSearchPreparerTest {

	private final CodebaseDomainCandidateProvider domainCandidateProvider = mock(CodebaseDomainCandidateProvider.class);
	private final LlmClient llmClient = mock(LlmClient.class);
	private final LlmReportSearchPreparer preparer = new LlmReportSearchPreparer(domainCandidateProvider, llmClient);

	@Test
	void preparesSearchInputFromOpenAiCompatibleResponse() {
		BugReport report = report();
		LlmConfig config = config();
		when(domainCandidateProvider.findCandidates(any())).thenReturn(List.of("Payment", "Order"));
		when(llmClient.completeJson(any(), any(), any(), any())).thenReturn(chatCompletion("""
				{
				  "reportType": "USER_REPORT",
				  "businessTerms": ["결제", "주문 내역"],
				  "candidateDomains": ["Payment", "Order"],
				  "symptoms": ["NOT_VISIBLE"],
				  "codeSearchTerms": ["payment", "order", "history"],
				  "confidence": "MEDIUM"
				}
				"""));

		ReportSearchPreparation preparation = preparer.prepare(report, config, "gpt-test");

		assertThat(preparation.reportType()).isEqualTo("USER_REPORT");
		assertThat(preparation.businessTerms()).containsExactly("결제", "주문 내역");
		assertThat(preparation.candidateDomains()).containsExactly("Payment", "Order");
		assertThat(preparation.symptoms()).containsExactly("NOT_VISIBLE");
		assertThat(preparation.codeSearchTerms()).containsExactly("payment", "order", "history");
		assertThat(preparation.confidence()).isEqualTo("MEDIUM");
	}

	@Test
	void filtersOutCandidateDomainsNotProvidedByCodebase() {
		BugReport report = report();
		LlmConfig config = config();
		when(domainCandidateProvider.findCandidates(any())).thenReturn(List.of("Payment", "Order"));
		when(llmClient.completeJson(any(), any(), any(), any())).thenReturn(chatCompletion("""
				{
				  "reportType": "USER_REPORT",
				  "businessTerms": ["결제"],
				  "candidateDomains": ["Payment", "Inventory"],
				  "symptoms": ["NOT_VISIBLE"],
				  "codeSearchTerms": ["payment"],
				  "confidence": "HIGH"
				}
				"""));

		ReportSearchPreparation preparation = preparer.prepare(report, config, "gpt-test");

		assertThat(preparation.candidateDomains()).containsExactly("Payment");
	}

	@Test
	void removesBlankAndDuplicateListValues() {
		BugReport report = report();
		LlmConfig config = config();
		when(domainCandidateProvider.findCandidates(any())).thenReturn(List.of("Payment"));
		when(llmClient.completeJson(any(), any(), any(), any())).thenReturn(chatCompletion("""
				{
				  "reportType": "USER_REPORT",
				  "businessTerms": ["결제", "", "결제", "주문"],
				  "candidateDomains": ["Payment", "Payment"],
				  "symptoms": ["NOT_VISIBLE", "NOT_VISIBLE"],
				  "codeSearchTerms": ["payment", "payment", "order"],
				  "confidence": "HIGH"
				}
				"""));

		ReportSearchPreparation preparation = preparer.prepare(report, config, "gpt-test");

		assertThat(preparation.businessTerms()).containsExactly("결제", "주문");
		assertThat(preparation.candidateDomains()).containsExactly("Payment");
		assertThat(preparation.symptoms()).containsExactly("NOT_VISIBLE");
		assertThat(preparation.codeSearchTerms()).containsExactly("payment", "order");
	}

	@Test
	void failsWhenResponseContentIsEmpty() {
		BugReport report = report();
		LlmConfig config = config();
		when(domainCandidateProvider.findCandidates(any())).thenReturn(List.of("Payment"));
		when(llmClient.completeJson(any(), any(), any(), any())).thenReturn("""
				{"choices":[{"message":{"content":""}}]}
				""");

		assertThatThrownBy(() -> preparer.prepare(report, config, "gpt-test"))
				.isInstanceOf(BusinessException.class)
				.hasMessage("LLM response content is empty");
	}

	@Test
	void failsWhenRequiredFieldIsMissing() {
		BugReport report = report();
		LlmConfig config = config();
		when(domainCandidateProvider.findCandidates(any())).thenReturn(List.of("Payment"));
		when(llmClient.completeJson(any(), any(), any(), any())).thenReturn(chatCompletion("""
				{
				  "businessTerms": ["결제"],
				  "candidateDomains": ["Payment"],
				  "symptoms": ["NOT_VISIBLE"],
				  "codeSearchTerms": ["payment"],
				  "confidence": "HIGH"
				}
				"""));

		assertThatThrownBy(() -> preparer.prepare(report, config, "gpt-test"))
				.isInstanceOf(BusinessException.class)
				.hasMessage("Invalid LLM search input response: missing field 'reportType'");
	}

	@Test
	void failsWhenListFieldIsNotArray() {
		BugReport report = report();
		LlmConfig config = config();
		when(domainCandidateProvider.findCandidates(any())).thenReturn(List.of("Payment"));
		when(llmClient.completeJson(any(), any(), any(), any())).thenReturn(chatCompletion("""
				{
				  "reportType": "USER_REPORT",
				  "businessTerms": "결제",
				  "candidateDomains": ["Payment"],
				  "symptoms": ["NOT_VISIBLE"],
				  "codeSearchTerms": ["payment"],
				  "confidence": "HIGH"
				}
				"""));

		assertThatThrownBy(() -> preparer.prepare(report, config, "gpt-test"))
				.isInstanceOf(BusinessException.class)
				.hasMessage("Invalid LLM search input response: field 'businessTerms' must be an array");
	}

	@Test
	void failsWhenListFieldContainsNonStringValue() {
		BugReport report = report();
		LlmConfig config = config();
		when(domainCandidateProvider.findCandidates(any())).thenReturn(List.of("Payment"));
		when(llmClient.completeJson(any(), any(), any(), any())).thenReturn(chatCompletion("""
				{
				  "reportType": "USER_REPORT",
				  "businessTerms": ["결제", 123],
				  "candidateDomains": ["Payment"],
				  "symptoms": ["NOT_VISIBLE"],
				  "codeSearchTerms": ["payment"],
				  "confidence": "HIGH"
				}
				"""));

		assertThatThrownBy(() -> preparer.prepare(report, config, "gpt-test"))
				.isInstanceOf(BusinessException.class)
				.hasMessage("Invalid LLM search input response: field 'businessTerms' must contain only strings");
	}

	@Test
	void failsWhenEnumFieldHasUnsupportedValue() {
		BugReport report = report();
		LlmConfig config = config();
		when(domainCandidateProvider.findCandidates(any())).thenReturn(List.of("Payment"));
		when(llmClient.completeJson(any(), any(), any(), any())).thenReturn(chatCompletion("""
				{
				  "reportType": "USER_REPORT",
				  "businessTerms": ["결제"],
				  "candidateDomains": ["Payment"],
				  "symptoms": ["PAYMENT_BROKEN"],
				  "codeSearchTerms": ["payment"],
				  "confidence": "HIGH"
				}
				"""));

		assertThatThrownBy(() -> preparer.prepare(report, config, "gpt-test"))
				.isInstanceOf(BusinessException.class)
				.hasMessage("Invalid LLM search input response: field 'symptoms' has unsupported value 'PAYMENT_BROKEN'");
	}

	@Test
	void failsWhenEnumListHasUnsupportedValueAfterListLimit() {
		BugReport report = report();
		LlmConfig config = config();
		when(domainCandidateProvider.findCandidates(any())).thenReturn(List.of("Payment"));
		when(llmClient.completeJson(any(), any(), any(), any())).thenReturn(chatCompletion("""
				{
				  "reportType": "USER_REPORT",
				  "businessTerms": ["결제"],
				  "candidateDomains": ["Payment"],
				  "symptoms": [
				    "NOT_VISIBLE", "NOT_VISIBLE", "NOT_VISIBLE", "NOT_VISIBLE", "NOT_VISIBLE",
				    "NOT_VISIBLE", "NOT_VISIBLE", "NOT_VISIBLE", "NOT_VISIBLE", "NOT_VISIBLE",
				    "NOT_VISIBLE", "NOT_VISIBLE", "NOT_VISIBLE", "NOT_VISIBLE", "NOT_VISIBLE",
				    "NOT_VISIBLE", "NOT_VISIBLE", "NOT_VISIBLE", "NOT_VISIBLE", "NOT_VISIBLE",
				    "PAYMENT_BROKEN"
				  ],
				  "codeSearchTerms": ["payment"],
				  "confidence": "HIGH"
				}
				"""));

		assertThatThrownBy(() -> preparer.prepare(report, config, "gpt-test"))
				.isInstanceOf(BusinessException.class)
				.hasMessage("Invalid LLM search input response: field 'symptoms' has unsupported value 'PAYMENT_BROKEN'");
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
		return new BugReport(project, "결제는 됐는데 주문 내역에 안 떠요", "카드 결제 성공 문자는 왔지만 주문 목록에 없습니다.");
	}

	private LlmConfig config() {
		return new LlmConfig(
				"openai",
				LlmProvider.OPENAI,
				"https://api.openai.com/v1",
				"sk-test",
				"gpt-test",
				true,
				true
		);
	}
}
