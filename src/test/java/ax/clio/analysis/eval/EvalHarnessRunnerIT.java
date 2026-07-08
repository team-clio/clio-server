package ax.clio.analysis.eval;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import ax.clio.analysis.CodeCandidateRanker;
import ax.clio.analysis.LlmReportSearchPreparer;
import ax.clio.analysis.ReportSearchInputBuilder;
import ax.clio.analysis.ReportSearchInputMode;
import ax.clio.llm.LlmConfig;
import ax.clio.llm.LlmConfigService;
import ax.clio.project.Project;
import ax.clio.project.ProjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * End-to-end ranking benchmark against a real scanned project and its git history.
 *
 * <p>Opt-in: skipped unless {@code CLIO_EVAL_REPO} is set, so normal CI never runs it.
 * To run, scan a project into clio, then:
 *
 * <pre>{@code
 * CLIO_EVAL_REPO=/path/to/repo \
 * CLIO_EVAL_PROJECT_ID=1 \
 * CLIO_EVAL_LIMIT=300 \
 * CLIO_EVAL_LLM_CONFIG_ID=1 \   # optional; omit for deterministic RAW_ONLY
 * ./gradlew test --tests '*EvalHarnessRunnerIT'
 * }</pre>
 *
 * Prints an {@link EvalScorecard}. Tune {@link CodeCandidateRanker} weights and re-run to
 * see the numbers move; the LLM step is snapshotted so re-runs are stable and free.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "CLIO_EVAL_REPO", matches = ".+")
class EvalHarnessRunnerIT {

	@Autowired
	private CodeCandidateRanker ranker;
	@Autowired
	private ReportSearchInputBuilder inputBuilder;
	@Autowired
	private ProjectService projectService;
	@Autowired
	private LlmReportSearchPreparer llmPreparer;
	@Autowired
	private LlmConfigService llmConfigService;

	@Test
	void runBenchmark() throws Exception {
		Path repoRoot = Path.of(System.getenv("CLIO_EVAL_REPO"));
		long projectId = Long.parseLong(env("CLIO_EVAL_PROJECT_ID", "1"));
		int scanLimit = Integer.parseInt(env("CLIO_EVAL_LIMIT", "300"));
		int maxRelevantFiles = Integer.parseInt(env("CLIO_EVAL_MAX_FILES", "10"));

		Project project = projectService.getProject(projectId);
		String prefix = PathReconciler.prefixOf(repoRoot, Path.of(project.getRootPath()));

		List<EvalCase> mined = new GroundTruthMiner().mine(repoRoot, scanLimit, maxRelevantFiles);
		List<EvalCase> cases = reconcile(mined, prefix);

		String llmConfigId = System.getenv("CLIO_EVAL_LLM_CONFIG_ID");
		boolean useLlm = llmConfigId != null && !llmConfigId.isBlank();
		RankingEvaluator.PreparationProvider provider = useLlm
				? snapshottedLlmProvider(Long.parseLong(llmConfigId))
				: RankingEvaluator.PreparationProvider.rawOnly();
		ReportSearchInputMode mode = useLlm ? ReportSearchInputMode.HYBRID : ReportSearchInputMode.RAW_ONLY;

		RankingEvaluator evaluator = new RankingEvaluator(inputBuilder, ranker, provider);
		EvalScorecard scorecard = evaluator.evaluateAll(projectId, cases, mode);

		System.out.println("\n" + scorecard.toReport()
				+ "\n  (mined " + mined.size() + " cases, " + cases.size() + " with in-project ground truth)\n");
	}

	/** Maps git paths into project-relative space and drops cases left with no ground truth. */
	private static List<EvalCase> reconcile(List<EvalCase> mined, String prefix) {
		List<EvalCase> reconciled = new ArrayList<>();
		for (EvalCase c : mined) {
			var relevant = PathReconciler.toProjectRelative(c.relevantFilePaths(), prefix);
			if (!relevant.isEmpty()) {
				reconciled.add(new EvalCase(c.id(), c.reportTitle(), c.reportBody(), relevant));
			}
		}
		return reconciled;
	}

	/** Live LLM preparation wrapped in a per-case snapshot cache for stable, free re-runs. */
	private RankingEvaluator.PreparationProvider snapshottedLlmProvider(long configId) {
		LlmConfig config = llmConfigService.getConfig(configId);
		String model = System.getenv("CLIO_EVAL_LLM_MODEL");
		RankingEvaluator.PreparationProvider live =
				(evalCase, report) -> llmPreparer.prepare(report, config, model);
		return new SnapshotPreparationProvider(live, Path.of("build", "eval-snapshots"));
	}

	private static String env(String name, String fallback) {
		String value = System.getenv(name);
		return value == null || value.isBlank() ? fallback : value;
	}
}
