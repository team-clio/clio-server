package ax.clio.agent.event;

import ax.clio.agent.client.ClioAgentClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "clio.agent", name = "enabled", havingValue = "true")
public class BugCollectedAgentDispatcher {
	private final ClioAgentClient agentClient;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void dispatch(BugCollectedEvent event) {
		try {
			agentClient.processBug(event.projectId(), event.bugId());
		} catch (RuntimeException exception) {
			log.error(
					"Failed to dispatch Bug processing to Clio Agent. projectId={}, bugId={}",
					event.projectId(),
					event.bugId(),
					exception
			);
		}
	}
}
