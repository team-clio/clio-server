package ax.clio.agent.event;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ax.clio.agent.client.ClioAgentClient;
import ax.clio.bug.service.BugLifecycleService;
import org.junit.jupiter.api.Test;

class BugCollectedAgentDispatcherTest {

	@Test
	void dispatchesTheCommittedBugToTheAgent() {
		ClioAgentClient client = mock(ClioAgentClient.class);
		BugLifecycleService lifecycleService = mock(BugLifecycleService.class);
		BugCollectedAgentDispatcher dispatcher = new BugCollectedAgentDispatcher(client, lifecycleService);

		dispatcher.dispatch(new BugCollectedEvent(3L, 72L));

		verify(lifecycleService).markAnalyzing(3L, 72L);
		verify(client).processBug(3L, 72L);
	}

	@Test
	void keepsBugCollectionSuccessfulWhenAgentDispatchFails() {
		ClioAgentClient client = mock(ClioAgentClient.class);
		BugLifecycleService lifecycleService = mock(BugLifecycleService.class);
		doThrow(new IllegalStateException("agent unavailable")).when(client).processBug(3L, 72L);
		BugCollectedAgentDispatcher dispatcher = new BugCollectedAgentDispatcher(client, lifecycleService);

		dispatcher.dispatch(new BugCollectedEvent(3L, 72L));

		verify(client).processBug(3L, 72L);
		verify(lifecycleService).markNew(3L, 72L);
	}
}
