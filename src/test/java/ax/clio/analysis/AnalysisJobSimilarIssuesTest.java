package ax.clio.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import ax.clio.project.Project;
import ax.clio.report.BugReport;
import org.junit.jupiter.api.Test;

class AnalysisJobSimilarIssuesTest {

	@Test
	void serializesAndReadsBackSimilarIssues() {
		BugReport report = new BugReport(new Project("p", "/r", "d"), "t", "desc");
		AnalysisJob job = new AnalysisJob(report, null, null, ReportSearchInputMode.RAW_ONLY);

		job.complete(draftWithSimilarIssues(List.of(
				new SimilarIssueEntry(42L, "결제 취소 오류", 0.87),
				new SimilarIssueEntry(43L, "환불 실패", 0.61))));

		List<SimilarIssueEntry> read = job.getSimilarIssues();
		assertThat(read).extracting(SimilarIssueEntry::reportId).containsExactly(42L, 43L);
		assertThat(read).extracting(SimilarIssueEntry::title).containsExactly("결제 취소 오류", "환불 실패");
		assertThat(read.get(0).score()).isEqualTo(0.87);
	}

	@Test
	void emptySimilarIssuesReadBackAsEmptyList() {
		BugReport report = new BugReport(new Project("p", "/r", "d"), "t", "desc");
		AnalysisJob job = new AnalysisJob(report, null, null, ReportSearchInputMode.RAW_ONLY);

		job.complete(draftWithSimilarIssues(List.of()));

		assertThat(job.getSimilarIssues()).isEmpty();
	}

	private AnalysisResultDraft draftWithSimilarIssues(List<SimilarIssueEntry> similarIssues) {
		return new AnalysisResultDraft(50, 50, 50, "UNKNOWN", "", "", "summary",
				List.of(), List.of(), "rationale", "fix", "tests", similarIssues, List.of(), List.of());
	}
}
