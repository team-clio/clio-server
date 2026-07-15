package ax.clio.analysis.pipeline;

import ax.clio.analysis.pipeline.contract.AnalysisResultDraft;
import ax.clio.analysis.pipeline.contract.GeneratedReport;
import ax.clio.analysis.pipeline.contract.RelatedCodeEntry;
import ax.clio.analysis.pipeline.contract.RelatedDecisionEntry;
import ax.clio.analysis.pipeline.contract.SimilarIssueEntry;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * withGeneratedReport는 summary·recommendedFix·recommendedTests 3필드만 교체하고 점수·근거·검색결과는
 * 그대로 둔다(roadmap #10 / 4.3, L5·원칙).
 */
class AnalysisResultDraftTest {

	@Test
	void withGeneratedReportReplacesOnlyTextFields() {
		AnalysisResultDraft base = new AnalysisResultDraft(
				70, 55, 60, "UNKNOWN", "kw", "Payment",
				"rule summary",
				List.of(new RelatedCodeEntry("A.java", "A", "SERVICE", "EXACT", 1, "s", 90, 2)),
				List.of(), "rule rationale", "rule fix", "rule tests",
				List.of(new SimilarIssueEntry(1L, "past", 0.9)),
				List.of(new RelatedDecisionEntry(2L, "decision", 0.8)),
				List.of());

		AnalysisResultDraft enriched = base.withGeneratedReport(
				new GeneratedReport("llm summary", "llm fix", "llm tests"));

		// 교체된 3필드
		assertThat(enriched.summary()).isEqualTo("llm summary");
		assertThat(enriched.recommendedFix()).isEqualTo("llm fix");
		assertThat(enriched.recommendedTests()).isEqualTo("llm tests");
		// 유지된 rule-based 필드
		assertThat(enriched.importanceScore()).isEqualTo(70);
		assertThat(enriched.difficultyScore()).isEqualTo(55);
		assertThat(enriched.riskScore()).isEqualTo(60);
		assertThat(enriched.rationale()).isEqualTo("rule rationale");
		assertThat(enriched.relatedCode()).isEqualTo(base.relatedCode());
		assertThat(enriched.similarIssues()).isEqualTo(base.similarIssues());
		assertThat(enriched.relatedDecisions()).isEqualTo(base.relatedDecisions());
	}
}
