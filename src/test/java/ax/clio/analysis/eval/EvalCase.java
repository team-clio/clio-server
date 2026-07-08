package ax.clio.analysis.eval;

import java.util.Set;

/**
 * One labeled evaluation instance mined from git history: a bug-report proxy
 * (the fixing commit's message) paired with the files that commit actually changed
 * (the ground-truth relevant code).
 *
 * @param id                 stable identifier (typically the commit SHA)
 * @param reportTitle        report-text proxy, first line of the commit message
 * @param reportBody         report-text proxy, remainder of the commit message
 * @param relevantFilePaths  project-relative paths the fix touched (ground truth)
 */
public record EvalCase(
		String id,
		String reportTitle,
		String reportBody,
		Set<String> relevantFilePaths) {
}
