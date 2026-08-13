package ax.clio.agent.event;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ax.clio.agent.client.ClioAgentClient;
import org.junit.jupiter.api.Test;

class BugCollectedAgentDispatcherTest {

	@Test
	void dispatchesTheCommittedBugToTheAgent() {
		ClioAgentClient client = mock(ClioAgentClient.class);
		BugCollectedAgentDispatcher dispatcher = new BugCollectedAgentDispatcher(client);

		dispatcher.dispatch(new BugCollectedEvent(3L, 72L));

		verify(client).processBug(3L, 72L);
	}

	@Test
	void keepsBugCollectionSuccessfulWhenAgentDispatchFails() {
		ClioAgentClient client = mock(ClioAgentClient.class);
		doThrow(new IllegalStateException("agent unavailable")).when(client).processBug(3L, 72L);
		BugCollectedAgentDispatcher dispatcher = new BugCollectedAgentDispatcher(client);

		dispatcher.dispatch(new BugCollectedEvent(3L, 72L));

		verify(client).processBug(3L, 72L);
	}
}
