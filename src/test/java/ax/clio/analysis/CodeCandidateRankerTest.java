package ax.clio.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import ax.clio.code.CodeSearchMatchType;
import ax.clio.code.CodeSearchResult;
import ax.clio.code.CodeSearchService;
import ax.clio.code.CodeSymbolType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CodeCandidateRankerTest {

	private CodeSearchService codeSearchService;
	private CodeCandidateRanker ranker;

	@BeforeEach
	void setUp() {
		codeSearchService = mock(CodeSearchService.class);
		ranker = new CodeCandidateRanker(codeSearchService);
	}

	@Test
	void singleKeywordInputKeepsBaseScore() {
		when(codeSearchService.search(eq(1L), eq("payment"), anyInt()))
				.thenReturn(List.of(symbol("PaymentService.java", "PaymentService",
						CodeSymbolType.CLASS, "SERVICE", CodeSearchMatchType.CLASS_NAME, 100)));

		List<RankedCodeCandidate> result = ranker.rank(1L, List.of(
				new ReportSearchInput("payment", ReportSearchInputType.KEYWORD)));

		assertThat(result).hasSize(1);
		RankedCodeCandidate c = result.getFirst();
		assertThat(c.baseScore()).isEqualTo(100);
		// KEYWORD multiplier = 1.0, hitCount=1 → no bonus
		assertThat(c.adjustedScore()).isEqualTo(100);
		assertThat(c.hitCount()).isEqualTo(1);
	}

	@Test
	void candidateDomainClassMatchAppliesMultiplier() {
		when(codeSearchService.search(eq(1L), eq("Payment"), anyInt()))
				.thenReturn(List.of(symbol("PaymentService.java", "PaymentService",
						CodeSymbolType.CLASS, "SERVICE", CodeSearchMatchType.CLASS_NAME, 100)));

		List<RankedCodeCandidate> result = ranker.rank(1L, List.of(
				new ReportSearchInput("Payment", ReportSearchInputType.CANDIDATE_DOMAIN)));

		RankedCodeCandidate c = result.getFirst();
		// 100 * 1.15 = 115
		assertThat(c.adjustedScore()).isEqualTo((int) Math.round(100 * CodeCandidateRanker.DOMAIN_CLASS_MULTIPLIER));
	}

	@Test
	void candidateDomainNonClassMatchAppliesSmallerMultiplier() {
		when(codeSearchService.search(eq(1L), eq("Payment"), anyInt()))
				.thenReturn(List.of(symbol("PaymentService.java", "processPayment",
						CodeSymbolType.METHOD, "SERVICE", CodeSearchMatchType.METHOD_NAME, 90)));

		List<RankedCodeCandidate> result = ranker.rank(1L, List.of(
				new ReportSearchInput("Payment", ReportSearchInputType.CANDIDATE_DOMAIN)));

		RankedCodeCandidate c = result.getFirst();
		// 90 * 1.05 = 94.5 → 95
		assertThat(c.adjustedScore()).isEqualTo((int) Math.round(90 * CodeCandidateRanker.DOMAIN_OTHER_MULTIPLIER));
	}

	@Test
	void rawReportMatchAppliesMultiplier() {
		when(codeSearchService.search(eq(1L), eq("결제 오류"), anyInt()))
				.thenReturn(List.of(codeText("ErrorHandler.java", 10, "// 결제 오류 처리", 50)));

		List<RankedCodeCandidate> result = ranker.rank(1L, List.of(
				new ReportSearchInput("결제 오류", ReportSearchInputType.RAW_REPORT)));

		RankedCodeCandidate c = result.getFirst();
		// 50 * 1.10 = 55
		assertThat(c.adjustedScore()).isEqualTo((int) Math.round(50 * CodeCandidateRanker.RAW_REPORT_MULTIPLIER));
	}

	@Test
	void symbolLevelHitCountBonus() {
		// 같은 심볼이 두 개의 다른 query에서 매칭
		when(codeSearchService.search(eq(1L), eq("payment"), anyInt()))
				.thenReturn(List.of(symbol("PaymentService.java", "PaymentService",
						CodeSymbolType.CLASS, "SERVICE", CodeSearchMatchType.CLASS_NAME, 100)));
		when(codeSearchService.search(eq(1L), eq("pay"), anyInt()))
				.thenReturn(List.of(symbol("PaymentService.java", "PaymentService",
						CodeSymbolType.CLASS, "SERVICE", CodeSearchMatchType.CLASS_NAME, 100)));

		List<RankedCodeCandidate> result = ranker.rank(1L, List.of(
				new ReportSearchInput("payment", ReportSearchInputType.KEYWORD),
				new ReportSearchInput("pay", ReportSearchInputType.KEYWORD)));

		assertThat(result).hasSize(1);
		RankedCodeCandidate c = result.getFirst();
		assertThat(c.hitCount()).isEqualTo(2);
		// 100 * 1.0 + (2-1)*10 = 110
		assertThat(c.adjustedScore()).isEqualTo(100 + CodeCandidateRanker.HIT_COUNT_BONUS);
	}

	@Test
	void differentSymbolsSameFileStaySeparateForHitCount() {
		// 같은 파일이지만 다른 심볼 → 각각 hitCount=1
		when(codeSearchService.search(eq(1L), eq("payment"), anyInt()))
				.thenReturn(List.of(symbol("PaymentService.java", "PaymentService",
						CodeSymbolType.CLASS, "SERVICE", CodeSearchMatchType.CLASS_NAME, 100)));
		when(codeSearchService.search(eq(1L), eq("order"), anyInt()))
				.thenReturn(List.of(symbol("PaymentService.java", "processOrder",
						CodeSymbolType.METHOD, "SERVICE", CodeSearchMatchType.METHOD_NAME, 90)));

		List<RankedCodeCandidate> result = ranker.rank(1L, List.of(
				new ReportSearchInput("payment", ReportSearchInputType.KEYWORD),
				new ReportSearchInput("order", ReportSearchInputType.KEYWORD)));

		// 파일 그룹화로 1개만 남음 (대표: 높은 점수)
		assertThat(result).hasSize(1);
		assertThat(result.getFirst().symbolName()).isEqualTo("PaymentService");
		assertThat(result.getFirst().hitCount()).isEqualTo(1);
	}

	@Test
	void hitCountBonusCapped() {
		for (String q : List.of("a", "b", "c", "d", "e")) {
			when(codeSearchService.search(eq(1L), eq(q), anyInt()))
					.thenReturn(List.of(symbol("Svc.java", "Svc",
							CodeSymbolType.CLASS, null, CodeSearchMatchType.CLASS_NAME, 100)));
		}

		List<RankedCodeCandidate> result = ranker.rank(1L, List.of(
				new ReportSearchInput("a", ReportSearchInputType.KEYWORD),
				new ReportSearchInput("b", ReportSearchInputType.KEYWORD),
				new ReportSearchInput("c", ReportSearchInputType.KEYWORD),
				new ReportSearchInput("d", ReportSearchInputType.KEYWORD),
				new ReportSearchInput("e", ReportSearchInputType.KEYWORD)));

		RankedCodeCandidate c = result.getFirst();
		assertThat(c.hitCount()).isEqualTo(5);
		// (5-1)*10 = 40, but capped at 30. Plus file match bonus (5 matches >= threshold)
		assertThat(c.adjustedScore()).isEqualTo(100 + CodeCandidateRanker.HIT_COUNT_BONUS_CAP + CodeCandidateRanker.FILE_MATCH_BONUS);
	}

	@Test
	void fileGroupingKeepsBestScore() {
		when(codeSearchService.search(eq(1L), eq("payment"), anyInt()))
				.thenReturn(List.of(
						symbol("PaymentService.java", "PaymentService",
								CodeSymbolType.CLASS, "SERVICE", CodeSearchMatchType.CLASS_NAME, 100),
						symbol("PaymentService.java", "pay",
								CodeSymbolType.METHOD, "SERVICE", CodeSearchMatchType.METHOD_NAME, 90),
						codeText("PaymentService.java", 55, "// payment", 50)));

		List<RankedCodeCandidate> result = ranker.rank(1L, List.of(
				new ReportSearchInput("payment", ReportSearchInputType.KEYWORD)));

		assertThat(result).hasSize(1);
		assertThat(result.getFirst().baseScore()).isEqualTo(100);
		assertThat(result.getFirst().symbolName()).isEqualTo("PaymentService");
	}

	@Test
	void fileMatchBonusAppliedWhenThresholdMet() {
		// 같은 파일에서 3개 이상 매칭 → FILE_MATCH_BONUS
		when(codeSearchService.search(eq(1L), eq("payment"), anyInt()))
				.thenReturn(List.of(
						symbol("PaymentService.java", "PaymentService",
								CodeSymbolType.CLASS, "SERVICE", CodeSearchMatchType.CLASS_NAME, 100),
						symbol("PaymentService.java", "pay",
								CodeSymbolType.METHOD, "SERVICE", CodeSearchMatchType.METHOD_NAME, 90),
						codeText("PaymentService.java", 55, "// payment", 50)));

		List<RankedCodeCandidate> result = ranker.rank(1L, List.of(
				new ReportSearchInput("payment", ReportSearchInputType.KEYWORD)));

		// 100 * 1.0 + 0 (hitCount=1) + 5 (fileMatch >= 3)
		assertThat(result.getFirst().adjustedScore()).isEqualTo(100 + CodeCandidateRanker.FILE_MATCH_BONUS);
	}

	@Test
	void fileMatchBonusNotAppliedBelowThreshold() {
		when(codeSearchService.search(eq(1L), eq("payment"), anyInt()))
				.thenReturn(List.of(
						symbol("PaymentService.java", "PaymentService",
								CodeSymbolType.CLASS, "SERVICE", CodeSearchMatchType.CLASS_NAME, 100)));

		List<RankedCodeCandidate> result = ranker.rank(1L, List.of(
				new ReportSearchInput("payment", ReportSearchInputType.KEYWORD)));

		// 1 match < threshold(3) → no file match bonus
		assertThat(result.getFirst().adjustedScore()).isEqualTo(100);
	}

	@Test
	void sortedByAdjustedScoreDescending() {
		when(codeSearchService.search(eq(1L), eq("service"), anyInt()))
				.thenReturn(List.of(
						symbol("OrderService.java", "OrderService",
								CodeSymbolType.CLASS, "SERVICE", CodeSearchMatchType.CLASS_NAME, 100),
						symbol("NotificationService.java", "NotificationService",
								CodeSymbolType.CLASS, "SERVICE", CodeSearchMatchType.CLASS_NAME, 100)));
		when(codeSearchService.search(eq(1L), eq("order"), anyInt()))
				.thenReturn(List.of(
						symbol("OrderService.java", "OrderService",
								CodeSymbolType.CLASS, "SERVICE", CodeSearchMatchType.CLASS_NAME, 100)));

		List<RankedCodeCandidate> result = ranker.rank(1L, List.of(
				new ReportSearchInput("service", ReportSearchInputType.KEYWORD),
				new ReportSearchInput("order", ReportSearchInputType.KEYWORD)));

		assertThat(result).hasSize(2);
		// OrderService has hitCount=2, NotificationService has hitCount=1
		assertThat(result.get(0).filePath()).isEqualTo("OrderService.java");
		assertThat(result.get(1).filePath()).isEqualTo("NotificationService.java");
	}

	@Test
	void maxResultsLimitedTo20() {
		java.util.List<CodeSearchResult> manyResults = new java.util.ArrayList<>();
		for (int i = 0; i < 25; i++) {
			manyResults.add(symbol("File" + i + ".java", "Class" + i,
					CodeSymbolType.CLASS, null, CodeSearchMatchType.CLASS_NAME, 100 - i));
		}
		when(codeSearchService.search(eq(1L), eq("query"), anyInt())).thenReturn(manyResults);

		List<RankedCodeCandidate> result = ranker.rank(1L, List.of(
				new ReportSearchInput("query", ReportSearchInputType.KEYWORD)));

		assertThat(result).hasSize(20);
	}

	@Test
	void symbolRoleIsPreserved() {
		when(codeSearchService.search(eq(1L), eq("order"), anyInt()))
				.thenReturn(List.of(
						symbol("OrderRepository.java", "OrderRepository",
								CodeSymbolType.CLASS, "REPOSITORY", CodeSearchMatchType.CLASS_NAME, 100),
						symbol("Order.java", "Order",
								CodeSymbolType.CLASS, "ENTITY", CodeSearchMatchType.CLASS_NAME, 100)));

		List<RankedCodeCandidate> result = ranker.rank(1L, List.of(
				new ReportSearchInput("order", ReportSearchInputType.KEYWORD)));

		assertThat(result).extracting(RankedCodeCandidate::symbolRole)
				.containsExactlyInAnyOrder("REPOSITORY", "ENTITY");
	}

	@Test
	void emptyInputsReturnEmpty() {
		assertThat(ranker.rank(1L, List.of())).isEmpty();
	}

	private CodeSearchResult symbol(String filePath, String name, CodeSymbolType type,
			String role, CodeSearchMatchType matchType, int score) {
		return new CodeSearchResult(1L, filePath, name, type, role, matchType, 1, name, score, false);
	}

	private CodeSearchResult codeText(String filePath, int line, String snippet, int score) {
		return new CodeSearchResult(1L, filePath, null, null, null,
				CodeSearchMatchType.CODE_TEXT, line, snippet, score, false);
	}
}
