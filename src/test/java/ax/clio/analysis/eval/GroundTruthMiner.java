package ax.clio.analysis.eval;

import ax.clio.code.entity.CodeFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Mines labeled evaluation cases from a project's git history.
 *
 * <p>Each bug-fixing commit becomes an {@link EvalCase}: the commit message is the
 * report-text proxy, and the source files the commit changed are the ground-truth
 * relevant code. See {@code 05-eval-harness.md} for the rationale.
 *
 * <p>Returned paths are repo-root relative (git's native form). If the analyzed
 * project's root is the repo root, these match {@code CodeFile.path} directly;
 * otherwise the caller must reconcile the prefix.
 */
public final class GroundTruthMiner {

	/** Field / record separators embedded in the git format string (ASCII control chars, absent from messages). */
	private static final String UNIT = String.valueOf((char) 0x1F);
	private static final String RECORD = String.valueOf((char) 0x1E);
	// RECORD starts each commit; three UNITs delimit sha|subject|body from the trailing --name-only list,
	// so blank lines inside a multi-paragraph body stay part of the body.
	private static final String FORMAT = RECORD + "%H" + UNIT + "%s" + UNIT + "%b" + UNIT;

	/** Heuristic for identifying bug-fix commits from subject + body. */
	private static final Pattern BUGFIX = Pattern.compile(
			"(?i)\\b(fix(e[sd])?|fixing|bug|bugfix|defect|regression|npe|crash|hotfix)\\b|#\\d+");

	private final GitCommandRunner git;

	public GroundTruthMiner() {
		this(GroundTruthMiner::runGit);
	}

	GroundTruthMiner(GitCommandRunner git) {
		this.git = git;
	}

	/**
	 * @param repoRoot         git working directory to mine
	 * @param scanLimit        how many recent commits to inspect
	 * @param maxRelevantFiles skip commits touching more source files than this (refactor noise)
	 */
	public List<EvalCase> mine(Path repoRoot, int scanLimit, int maxRelevantFiles)
			throws IOException, InterruptedException {
		String out = git.run(repoRoot, List.of(
				"log", "--no-merges", "-n", Integer.toString(scanLimit),
				"--name-only", "--format=" + FORMAT));
		return parse(out, maxRelevantFiles);
	}

	static List<EvalCase> parse(String out, int maxRelevantFiles) {
		List<EvalCase> cases = new ArrayList<>();
		for (String chunk : out.split(RECORD)) {
			if (chunk.isBlank()) {
				continue;
			}
			int unit1 = chunk.indexOf(UNIT);
			int unit2 = unit1 < 0 ? -1 : chunk.indexOf(UNIT, unit1 + 1);
			int unit3 = unit2 < 0 ? -1 : chunk.indexOf(UNIT, unit2 + 1);
			if (unit3 < 0) {
				continue;
			}
			String sha = chunk.substring(0, unit1).strip();
			String subject = chunk.substring(unit1 + 1, unit2).strip();
			String body = chunk.substring(unit2 + 1, unit3).strip();
			String fileBlock = chunk.substring(unit3 + 1);

			if (!BUGFIX.matcher(subject + "\n" + body).find()) {
				continue;
			}

			Set<String> files = new LinkedHashSet<>();
			for (String line : fileBlock.split("\n")) {
				String path = line.strip();
				if (path.endsWith(".java")) {
					files.add(path);
				}
			}
			if (files.isEmpty() || files.size() > maxRelevantFiles) {
				continue;
			}
			cases.add(new EvalCase(sha, subject, body, files));
		}
		return cases;
	}

	/** Seam so the parser can be unit-tested without invoking git. */
	interface GitCommandRunner {
		String run(Path repoRoot, List<String> args) throws IOException, InterruptedException;
	}

	private static String runGit(Path repoRoot, List<String> args) throws IOException, InterruptedException {
		List<String> command = new ArrayList<>();
		command.add("git");
		command.add("-C");
		command.add(repoRoot.toString());
		command.addAll(args);

		Process process = new ProcessBuilder(command).redirectErrorStream(false).start();
		String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		int exit = process.waitFor();
		if (exit != 0) {
			throw new IOException("git " + args + " failed with exit code " + exit);
		}
		return stdout;
	}
}
