package ax.clio.analysis.eval;

import ax.clio.analysis.job.service.AnalysisWorker;
import ax.clio.analysis.pipeline.contract.RankedCodeCandidate;
import ax.clio.analysis.pipeline.contract.ReportSearchInput;
import ax.clio.analysis.pipeline.contract.ReportSearchInputMode;
import ax.clio.analysis.pipeline.contract.ReportSearchPreparation;
import ax.clio.analysis.prepare.LlmReportSearchPreparer;
import ax.clio.analysis.search.CodeCandidateRanker;
import ax.clio.analysis.search.ReportSearchInputBuilder;

import java.util.List;

import ax.clio.project.entity.Project;
import ax.clio.report.entity.BugReport;

/**
 * Drives the real search + rank pipeline against mined {@link EvalCase}s and scores the
 * output against ground truth.
 *
 * <p>Mirrors the search/rank portion of {@code AnalysisWorker.run()}
 * (build inputs → search → rank), but pulls the LLM preparation step out behind
 * {@link PreparationProvider} so a run can use raw-only, live LLM, or a replayed snapshot.
 *
 * <p>Assumes {@link EvalCase#relevantFilePaths()} and the ranker's {@code filePath()} use
 * the same path convention; the caller (runner) reconciles repo-root vs project-root prefixes.
 */
public final class RankingEvaluator {

	private final ReportSearchInputBuilder inputBuilder;
	private final CodeCandidateRanker ranker;
	private final PreparationProvider preparationProvider;

	public RankingEvaluator(ReportSearchInputBuilder inputBuilder, CodeCandidateRanker ranker,
			PreparationProvider preparationProvider) {
		this.inputBuilder = inputBuilder;
		this.ranker = ranker;
		this.preparationProvider = preparationProvider;
	}

	/** Runs one case through the pipeline and scores it. */
	public CaseOutcome evaluate(long projectId, EvalCase evalCase, ReportSearchInputMode mode) {
		BugReport report = toTransientReport(evalCase);
		ReportSearchPreparation preparation = preparationProvider.prepare(evalCase, report);
		List<ReportSearchInput> inputs = inputBuilder.build(report, preparation, mode);
		List<RankedCodeCandidate> candidates = ranker.rank(projectId, inputs);

		List<String> rankedFiles = candidates.stream()
				.map(RankedCodeCandidate::filePath)
				.distinct()
				.toList();
		return CaseOutcome.of(evalCase.id(), rankedFiles, evalCase.relevantFilePaths());
	}

	/** Runs every case and aggregates into a scorecard. */
	public EvalScorecard evaluateAll(long projectId, List<EvalCase> cases, ReportSearchInputMode mode) {
		List<CaseOutcome> outcomes = cases.stream()
				.map(evalCase -> evaluate(projectId, evalCase, mode))
				.toList();
		return EvalScorecard.aggregate(outcomes);
	}

	private static BugReport toTransientReport(EvalCase evalCase) {
		// Not persisted; only title/description are read downstream.
		Project project = new Project("eval", ".", null);
		return new BugReport(project, evalCase.reportTitle(), evalCase.reportBody());
	}

	/**
	 * Supplies the LLM structuring step for a case. Implementations: raw-only (no LLM),
	 * live {@code LlmReportSearchPreparer}, or a snapshot cache (Step D).
	 */
	@FunctionalInterface
	public interface PreparationProvider {
		ReportSearchPreparation prepare(EvalCase evalCase, BugReport report);

		/** Deterministic, LLM-free provider: search runs on raw report text only. */
		static PreparationProvider rawOnly() {
			return (evalCase, report) -> ReportSearchPreparation.rawOnly();
		}
	}
}
