package ax.clio.agent.event;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import ax.clio.agent.client.ClioAgentClient;
import ax.clio.bug.service.BugLifecycleService;
import ax.clio.bug.repository.BugRepository;
import ax.clio.project.entity.ProjectSource;
import ax.clio.project.entity.ProjectSourceSyncStatus;
import ax.clio.project.repository.ProjectSourceRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class BugCollectedAgentDispatcherTest {

	@Test
	void dispatchesTheCommittedBugToTheAgent() {
		ClioAgentClient client = mock(ClioAgentClient.class);
		BugLifecycleService lifecycleService = mock(BugLifecycleService.class);
		BugRepository bugRepository = mock(BugRepository.class);
		ProjectSourceRepository sourceRepository = readySourceRepository();
		when(lifecycleService.claimForAnalysis(3L, 72L)).thenReturn(true);
		BugCollectedAgentDispatcher dispatcher = new BugCollectedAgentDispatcher(client, lifecycleService, bugRepository, sourceRepository);

		dispatcher.dispatch(new BugCollectedEvent(3L, 72L));

		verify(lifecycleService).claimForAnalysis(3L, 72L);
		verify(client).processBug(3L, 72L);
	}

	@Test
	void keepsBugCollectionSuccessfulWhenAgentDispatchFails() {
		ClioAgentClient client = mock(ClioAgentClient.class);
		BugLifecycleService lifecycleService = mock(BugLifecycleService.class);
		BugRepository bugRepository = mock(BugRepository.class);
		ProjectSourceRepository sourceRepository = readySourceRepository();
		when(lifecycleService.claimForAnalysis(3L, 72L)).thenReturn(true);
		doThrow(new IllegalStateException("agent unavailable")).when(client).processBug(3L, 72L);
		BugCollectedAgentDispatcher dispatcher = new BugCollectedAgentDispatcher(client, lifecycleService, bugRepository, sourceRepository);

		dispatcher.dispatch(new BugCollectedEvent(3L, 72L));

		verify(client).processBug(3L, 72L);
		verify(lifecycleService).markNew(3L, 72L);
	}

	@Test
	void keepsBugNewWhenNoRepositoryIsReady() {
		ClioAgentClient client = mock(ClioAgentClient.class);
		BugLifecycleService lifecycleService = mock(BugLifecycleService.class);
		BugRepository bugRepository = mock(BugRepository.class);
		ProjectSourceRepository sourceRepository = mock(ProjectSourceRepository.class);
		when(sourceRepository.findAllByProjectIdAndEnabledTrue(3L)).thenReturn(List.of());
		BugCollectedAgentDispatcher dispatcher = new BugCollectedAgentDispatcher(client, lifecycleService, bugRepository, sourceRepository);

		dispatcher.dispatch(new BugCollectedEvent(3L, 72L));

		verify(client, org.mockito.Mockito.never()).processBug(3L, 72L);
		verify(lifecycleService, org.mockito.Mockito.never()).claimForAnalysis(3L, 72L);
	}

	private ProjectSourceRepository readySourceRepository() {
		ProjectSourceRepository repository = mock(ProjectSourceRepository.class);
		ProjectSource source = mock(ProjectSource.class);
		when(source.getSyncStatus()).thenReturn(ProjectSourceSyncStatus.SYNCED);
		when(repository.findAllByProjectIdAndEnabledTrue(3L)).thenReturn(List.of(source));
		return repository;
	}
}
