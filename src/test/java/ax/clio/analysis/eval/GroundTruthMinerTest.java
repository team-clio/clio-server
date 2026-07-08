package ax.clio.analysis.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GroundTruthMinerTest {

	private static final String UNIT = String.valueOf((char) 0x1F);
	private static final String RECORD = String.valueOf((char) 0x1E);

	/** Builds output shaped like `git log --name-only` with our format string. */
	private static String gitOutput(String sha, String subject, String body, String... files) {
		return RECORD + sha + UNIT + subject + UNIT + body + UNIT + "\n\n" + String.join("\n", files) + "\n";
	}

	@Test
	void parsesBugfixCommitIntoCase() {
		String out = gitOutput("abc123", "Fix NPE in payment cancellation", "closes #42",
				"src/main/java/PaymentService.java", "src/main/java/RefundValidator.java");

		List<EvalCase> cases = GroundTruthMiner.parse(out, 10);

		assertThat(cases).hasSize(1);
		EvalCase c = cases.getFirst();
		assertThat(c.id()).isEqualTo("abc123");
		assertThat(c.reportTitle()).isEqualTo("Fix NPE in payment cancellation");
		assertThat(c.relevantFilePaths()).containsExactlyInAnyOrder(
				"src/main/java/PaymentService.java", "src/main/java/RefundValidator.java");
	}

	@Test
	void skipsNonBugfixCommits() {
		String out = gitOutput("d1", "docs: update README", "", "README.md")
				+ gitOutput("d2", "refactor: rename package", "", "src/main/java/Foo.java");

		assertThat(GroundTruthMiner.parse(out, 10)).isEmpty();
	}

	@Test
	void keepsOnlyJavaFiles() {
		String out = gitOutput("a1", "fix: broken migration", "",
				"src/main/java/Migrator.java", "README.md", "db/schema.sql");

		assertThat(GroundTruthMiner.parse(out, 10).getFirst().relevantFilePaths())
				.containsExactly("src/main/java/Migrator.java");
	}

	@Test
	void skipsCommitsExceedingFileThreshold() {
		String out = gitOutput("big", "fix: sweeping change", "",
				"A.java", "B.java", "C.java", "D.java");

		assertThat(GroundTruthMiner.parse(out, 3)).isEmpty();
	}

	@Test
	void multiParagraphBodyDoesNotLeakIntoFiles() {
		// Body contains a blank line; it must stay in the body, not become a "file".
		String out = gitOutput("m1", "fix: race condition", "first paragraph\n\nsecond paragraph",
				"src/main/java/Worker.java");

		EvalCase c = GroundTruthMiner.parse(out, 10).getFirst();
		assertThat(c.reportBody()).contains("second paragraph");
		assertThat(c.relevantFilePaths()).containsExactly("src/main/java/Worker.java");
	}

	@Test
	void minesFromRealTempRepo(@TempDir Path repo) throws IOException, InterruptedException {
		run(repo, "git", "init", "-q");
		run(repo, "git", "config", "user.email", "t@example.com");
		run(repo, "git", "config", "user.name", "Test");

		Files.writeString(repo.resolve("Foo.java"), "class Foo {}");
		run(repo, "git", "add", "-A");
		run(repo, "git", "commit", "-q", "-m", "feat: add Foo");

		Files.writeString(repo.resolve("Foo.java"), "class Foo { int x; }");
		run(repo, "git", "add", "-A");
		run(repo, "git", "commit", "-q", "-m", "fix: correct Foo field bug");

		List<EvalCase> cases = new GroundTruthMiner().mine(repo, 50, 10);

		assertThat(cases).hasSize(1);
		assertThat(cases.getFirst().reportTitle()).isEqualTo("fix: correct Foo field bug");
		assertThat(cases.getFirst().relevantFilePaths()).containsExactly("Foo.java");
	}

	private static void run(Path dir, String... command) throws IOException, InterruptedException {
		Process p = new ProcessBuilder(command).directory(dir.toFile()).inheritIO().start();
		if (p.waitFor() != 0) {
			throw new IOException("command failed: " + String.join(" ", command));
		}
	}
}
