package ax.clio.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import ax.clio.project.Project;
import ax.clio.report.BugReport;
import org.junit.jupiter.api.Test;

class ReportSearchInputBuilderTest {

	private final ReportSearchInputBuilder builder = new ReportSearchInputBuilder();

	@Test
	void rawOnlyUsesTitleAndDescriptionOnly() {
		BugReport report = report();
		ReportSearchPreparation preparation = preparation();

		List<ReportSearchInput> inputs = builder.build(report, preparation, ReportSearchInputMode.RAW_ONLY);

		assertThat(inputs).containsExactly(
				new ReportSearchInput("결제는 됐는데 주문 내역에 안 떠요", ReportSearchInputType.RAW_REPORT),
				new ReportSearchInput("카드 결제 성공 문자는 왔지만 주문 목록에 없습니다.", ReportSearchInputType.RAW_REPORT)
		);
	}

	@Test
	void preparedOnlyUsesLlmPreparedTermsOnly() {
		BugReport report = report();
		ReportSearchPreparation preparation = preparation();

		List<ReportSearchInput> inputs = builder.build(report, preparation, ReportSearchInputMode.PREPARED_ONLY);

		assertThat(inputs).containsExactly(
				new ReportSearchInput("Payment", ReportSearchInputType.CANDIDATE_DOMAIN),
				new ReportSearchInput("Order", ReportSearchInputType.CANDIDATE_DOMAIN),
				new ReportSearchInput("결제", ReportSearchInputType.KEYWORD),
				new ReportSearchInput("주문 내역", ReportSearchInputType.KEYWORD),
				new ReportSearchInput("payment", ReportSearchInputType.KEYWORD),
				new ReportSearchInput("order", ReportSearchInputType.KEYWORD),
				new ReportSearchInput("history", ReportSearchInputType.KEYWORD)
		);
	}

	@Test
	void hybridKeepsRawAndPreparedInputsInOrder() {
		BugReport report = report();
		ReportSearchPreparation preparation = preparation();

		List<ReportSearchInput> inputs = builder.build(report, preparation, ReportSearchInputMode.HYBRID);

		assertThat(inputs).containsExactly(
				new ReportSearchInput("결제는 됐는데 주문 내역에 안 떠요", ReportSearchInputType.RAW_REPORT),
				new ReportSearchInput("카드 결제 성공 문자는 왔지만 주문 목록에 없습니다.", ReportSearchInputType.RAW_REPORT),
				new ReportSearchInput("Payment", ReportSearchInputType.CANDIDATE_DOMAIN),
				new ReportSearchInput("Order", ReportSearchInputType.CANDIDATE_DOMAIN),
				new ReportSearchInput("결제", ReportSearchInputType.KEYWORD),
				new ReportSearchInput("주문 내역", ReportSearchInputType.KEYWORD),
				new ReportSearchInput("payment", ReportSearchInputType.KEYWORD),
				new ReportSearchInput("order", ReportSearchInputType.KEYWORD),
				new ReportSearchInput("history", ReportSearchInputType.KEYWORD)
		);
	}

	private BugReport report() {
		Project project = new Project("shop", "/workspace/shop", "shop service");
		return new BugReport(project, "결제는 됐는데 주문 내역에 안 떠요", "카드 결제 성공 문자는 왔지만 주문 목록에 없습니다.");
	}

	private ReportSearchPreparation preparation() {
		return new ReportSearchPreparation(
				"USER_REPORT",
				List.of("결제", "주문 내역"),
				List.of("Payment", "Order"),
				List.of("NOT_VISIBLE"),
				List.of("payment", "order", "history"),
				"MEDIUM"
		);
	}
}
