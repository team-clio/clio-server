package ax.clio.analysis.eval;

import ax.clio.analysis.pipeline.ReportSearchPreparation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import ax.clio.report.BugReport;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Caches each case's LLM structuring result to disk so the (non-deterministic, paid) LLM
 * is called at most once per case; subsequent runs replay the snapshot.
 *
 * <p>This is what lets the harness measure the full pipeline (D13) while keeping weight
 * tuning a stable, repeatable, free optimization target.
 */
public final class SnapshotPreparationProvider implements RankingEvaluator.PreparationProvider {

	private final RankingEvaluator.PreparationProvider delegate;
	private final Path cacheDir;
	private final ObjectMapper mapper = new ObjectMapper();

	/**
	 * @param delegate  live provider (e.g. the LLM preparer) invoked on a cache miss
	 * @param cacheDir  directory holding one {@code <caseId>.json} snapshot per case
	 */
	public SnapshotPreparationProvider(RankingEvaluator.PreparationProvider delegate, Path cacheDir) {
		this.delegate = delegate;
		this.cacheDir = cacheDir;
		try {
			Files.createDirectories(cacheDir);
		} catch (IOException e) {
			throw new UncheckedIOException("cannot create snapshot cache dir " + cacheDir, e);
		}
	}

	@Override
	public ReportSearchPreparation prepare(EvalCase evalCase, BugReport report) {
		Path snapshot = cacheDir.resolve(sanitize(evalCase.id()) + ".json");
		try {
			if (Files.exists(snapshot)) {
				return mapper.readValue(Files.readString(snapshot), ReportSearchPreparation.class);
			}
			ReportSearchPreparation prepared = delegate.prepare(evalCase, report);
			Files.writeString(snapshot, mapper.writeValueAsString(prepared));
			return prepared;
		} catch (IOException e) {
			throw new UncheckedIOException("snapshot IO failed for case " + evalCase.id(), e);
		}
	}

	/** Commit SHAs are safe, but guard against path-hostile characters in any id. */
	private static String sanitize(String id) {
		return id.replaceAll("[^A-Za-z0-9_.-]", "_");
	}
}
