package ax.clio.analysis.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import ax.clio.analysis.ReportSearchPreparation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotPreparationProviderTest {

	private static final EvalCase CASE =
			new EvalCase("sha-abc", "fix bug", "body", Set.of("Foo.java"));

	private static ReportSearchPreparation samplePreparation() {
		return new ReportSearchPreparation(
				"BUG", List.of("payment"), List.of("Payment"), List.of("ERROR"), List.of("cancel"), "HIGH");
	}

	@Test
	void callsDelegateOnceThenReplaysFromDisk(@TempDir Path cacheDir) {
		AtomicInteger calls = new AtomicInteger();
		RankingEvaluator.PreparationProvider counting = (c, r) -> {
			calls.incrementAndGet();
			return samplePreparation();
		};
		SnapshotPreparationProvider provider = new SnapshotPreparationProvider(counting, cacheDir);

		provider.prepare(CASE, null);
		provider.prepare(CASE, null);

		assertThat(calls.get()).isEqualTo(1);
	}

	@Test
	void replayReturnsEquivalentPreparation(@TempDir Path cacheDir) {
		RankingEvaluator.PreparationProvider live = (c, r) -> samplePreparation();
		// First run writes the snapshot; a fresh provider (cold) reads it back.
		new SnapshotPreparationProvider(live, cacheDir).prepare(CASE, null);

		RankingEvaluator.PreparationProvider fail = (c, r) -> {
			throw new AssertionError("delegate must not be called on replay");
		};
		ReportSearchPreparation replayed = new SnapshotPreparationProvider(fail, cacheDir).prepare(CASE, null);

		assertThat(replayed).isEqualTo(samplePreparation());
	}

	@Test
	void persistsSnapshotFileNamedByCaseId(@TempDir Path cacheDir) {
		new SnapshotPreparationProvider((c, r) -> samplePreparation(), cacheDir).prepare(CASE, null);
		assertThat(cacheDir.resolve("sha-abc.json")).exists();
	}
}
