package ax.clio.agent.event;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ax.clio.agent.client.ClioAgentClient;
import ax.clio.agent.client.RepositorySyncRequestType;
import ax.clio.project.service.ProjectService;
import org.junit.jupiter.api.Test;

class RepositorySyncAgentDispatcherTest {

	private final ClioAgentClient client = mock(ClioAgentClient.class);
	private final ProjectService projectService = mock(ProjectService.class);
	private final RepositorySyncAgentDispatcher dispatcher =
			new RepositorySyncAgentDispatcher(client, projectService);

	@Test
	void dispatchesRepositoryAddedAndMarksSyncing() {
		dispatcher.dispatch(new RepositorySyncEvent(
				7L, 42L, RepositorySyncRequestType.REPOSITORY_ADDED, "main",
				"https://git.example.internal/team/app.git"
		));

		verify(client).dispatchRepositorySync(
				7L, 42L, RepositorySyncRequestType.REPOSITORY_ADDED, "main",
				"https://git.example.internal/team/app.git"
		);
		verify(projectService).markRepositorySyncing(7L, 42L);
		verify(projectService, never()).markRepositorySyncFailed(7L, 42L);
	}

	@Test
	void keepsRepositoryCreationSuccessfulWhenAgentDispatchFails() {
		doThrow(new IllegalStateException("agent unavailable")).when(client).dispatchRepositorySync(
				7L, 42L, RepositorySyncRequestType.REPOSITORY_ADDED, "main",
				"https://git.example.internal/team/app.git"
		);

		dispatcher.dispatch(new RepositorySyncEvent(
				7L, 42L, RepositorySyncRequestType.REPOSITORY_ADDED, "main",
				"https://git.example.internal/team/app.git"
		));

		verify(projectService, never()).markRepositorySyncing(7L, 42L);
		verify(projectService).markRepositorySyncFailed(7L, 42L);
	}

	@Test
	void dispatchesRepositoryRemovedWithoutTouchingDeletedRowStatus() {
		dispatcher.dispatch(new RepositorySyncEvent(
				7L, 42L, RepositorySyncRequestType.REPOSITORY_REMOVED, "main", null
		));

		verify(client).dispatchRepositorySync(
				7L, 42L, RepositorySyncRequestType.REPOSITORY_REMOVED, "main", null
		);
		verify(projectService, never()).markRepositorySyncing(7L, 42L);
		verify(projectService, never()).markRepositorySyncFailed(7L, 42L);
	}
}
