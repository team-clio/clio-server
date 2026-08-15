package ax.clio.agent.event;

import ax.clio.agent.client.ClioAgentClient;
import ax.clio.bug.service.BugLifecycleService;
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
	private final BugLifecycleService bugLifecycleService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void dispatch(BugCollectedEvent event) {
		try {
			bugLifecycleService.markAnalyzing(event.projectId(), event.bugId());
			agentClient.processBug(event.projectId(), event.bugId());
		} catch (RuntimeException exception) {
			log.error(
					"Failed to dispatch Bug processing to Clio Agent. projectId={}, bugId={}",
					event.projectId(),
					event.bugId(),
					exception
			);
			revertToNew(event.projectId(), event.bugId());
		}
	}

	private void revertToNew(Long projectId, Long bugId) {
		try {
			bugLifecycleService.markNew(projectId, bugId);
		} catch (RuntimeException rollbackError) {
			log.error(
					"Failed to revert Bug to NEW after dispatch failure. projectId={}, bugId={}",
					projectId,
					bugId,
					rollbackError
			);
		}
	}
}
