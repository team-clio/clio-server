package ax.clio.project.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProjectSourceTest {

	@Test
	void marksSuccessfulSynchronizationWithCompletionTime() {
		ProjectSource source = ProjectSource.create(
				Project.create("Clio", null),
				RepositoryProvider.GITHUB,
				"acme",
				"web",
				"https://github.com/acme/web",
				"main",
				null,
				null,
				true
		);

		source.markSynced();

		assertThat(source.getSyncStatus()).isEqualTo(ProjectSourceSyncStatus.SYNCED);
		assertThat(source.getLastSyncedAt()).isNotNull();
	}
}
