package ax.clio.analysis.eval;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Reconciles git's repo-root-relative paths with {@code CodeFile.path}, which is relative
 * to {@code Project.rootPath}. When the project root is a subdirectory of the repo, the
 * subdirectory prefix is stripped and files outside the project subtree are dropped.
 */
public final class PathReconciler {

	private PathReconciler() {
	}

	/**
	 * @param repoRelative git-mined paths (relative to the repo root)
	 * @param prefix       {@code repoRoot.relativize(projectRoot)}; empty/"." when they coincide
	 * @return the same paths made relative to the project root
	 */
	public static Set<String> toProjectRelative(Set<String> repoRelative, String prefix) {
		String normalized = normalizePrefix(prefix);
		Set<String> result = new LinkedHashSet<>();
		for (String raw : repoRelative) {
			String path = raw.replace('\\', '/');
			if (normalized.isEmpty()) {
				result.add(path);
			} else if (path.startsWith(normalized)) {
				result.add(path.substring(normalized.length()));
			}
			// otherwise the file lives outside the project subtree → dropped
		}
		return result;
	}

	/** Normalizes a directory prefix to "" or a "foo/bar/"-form with a trailing slash. */
	static String normalizePrefix(String prefix) {
		if (prefix == null || prefix.isBlank() || prefix.equals(".")) {
			return "";
		}
		String normalized = prefix.replace('\\', '/');
		return normalized.endsWith("/") ? normalized : normalized + "/";
	}

	/** Derives the prefix from concrete paths. Assumes {@code projectRoot} is within {@code repoRoot}. */
	public static String prefixOf(Path repoRoot, Path projectRoot) {
		Path rel = repoRoot.toAbsolutePath().normalize()
				.relativize(projectRoot.toAbsolutePath().normalize());
		return rel.toString();
	}
}
