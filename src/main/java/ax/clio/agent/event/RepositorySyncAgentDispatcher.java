package ax.clio.agent.event;

import ax.clio.agent.client.ClioAgentClient;
import ax.clio.agent.client.RepositorySyncRequestType;
import ax.clio.project.service.ProjectService;
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
public class RepositorySyncAgentDispatcher {
	private final ClioAgentClient agentClient;
	private final ProjectService projectService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void dispatch(RepositorySyncEvent event) {
		try {
			agentClient.dispatchRepositorySync(
					event.projectId(),
					event.sourceId(),
					event.requestType(),
					event.branch(),
					event.sourceUri()
			);
			if (event.requestType() == RepositorySyncRequestType.REPOSITORY_ADDED) {
				projectService.markRepositorySyncing(event.projectId(), event.sourceId());
			}
		} catch (RuntimeException exception) {
			log.error(
					"Failed to dispatch Repository sync to Clio Agent. projectId={}, sourceId={}",
					event.projectId(),
					event.sourceId(),
					exception
			);
			if (event.requestType() == RepositorySyncRequestType.REPOSITORY_ADDED) {
				projectService.markRepositorySyncFailed(event.projectId(), event.sourceId());
			}
		}
	}
}
