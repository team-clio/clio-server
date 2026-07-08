package ax.clio.analysis.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;

class PathReconcilerTest {

	@Test
	void identityWhenProjectRootIsRepoRoot() {
		Set<String> paths = Set.of("src/main/java/Foo.java", "src/main/java/Bar.java");
		assertThat(PathReconciler.toProjectRelative(paths, "")).isEqualTo(paths);
	}

	@Test
	void stripsSubdirectoryPrefix() {
		Set<String> repoRelative = Set.of("backend/src/Foo.java", "backend/src/Bar.java");
		assertThat(PathReconciler.toProjectRelative(repoRelative, "backend"))
				.containsExactlyInAnyOrder("src/Foo.java", "src/Bar.java");
	}

	@Test
	void dropsFilesOutsideProjectSubtree() {
		Set<String> repoRelative = Set.of("backend/src/Foo.java", "frontend/app.java", "README.java");
		assertThat(PathReconciler.toProjectRelative(repoRelative, "backend"))
				.containsExactly("src/Foo.java");
	}

	@Test
	void normalizePrefixHandlesDotAndTrailingSlash() {
		assertThat(PathReconciler.normalizePrefix(".")).isEmpty();
		assertThat(PathReconciler.normalizePrefix("")).isEmpty();
		assertThat(PathReconciler.normalizePrefix("backend")).isEqualTo("backend/");
		assertThat(PathReconciler.normalizePrefix("backend/")).isEqualTo("backend/");
	}

	@Test
	void prefixOfDerivesSubdirectory() {
		Path repo = Path.of("/tmp/repo");
		Path project = Path.of("/tmp/repo/backend");
		assertThat(PathReconciler.prefixOf(repo, project)).isEqualTo("backend");
	}

	@Test
	void prefixOfIsEmptyWhenEqual() {
		Path repo = Path.of("/tmp/repo");
		assertThat(PathReconciler.prefixOf(repo, repo)).isEmpty();
	}
}
