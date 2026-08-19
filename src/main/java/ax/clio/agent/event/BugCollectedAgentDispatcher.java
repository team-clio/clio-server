package ax.clio.agent.event;

import ax.clio.agent.client.ClioAgentClient;
import ax.clio.bug.service.BugLifecycleService;
import ax.clio.bug.entity.BugStatus;
import ax.clio.bug.repository.BugRepository;
import ax.clio.project.entity.ProjectSourceSyncStatus;
import ax.clio.project.repository.ProjectSourceRepository;
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
	private final BugRepository bugRepository;
	private final ProjectSourceRepository projectSourceRepository;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void dispatch(BugCollectedEvent event) {
		if (!isReady(event.projectId())) {
			return;
		}
		try {
			if (!bugLifecycleService.claimForAnalysis(event.projectId(), event.bugId())) {
				return;
			}
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

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void releaseWaitingBugs(RepositorySyncCompletedEvent event) {
		if (!isReady(event.projectId())) {
			return;
		}
		bugRepository.findIdsByProjectIdAndStatusOrderByIdAsc(event.projectId(), BugStatus.NEW)
				.forEach(bugId -> dispatch(new BugCollectedEvent(event.projectId(), bugId)));
	}

	private boolean isReady(Long projectId) {
		var sources = projectSourceRepository.findAllByProjectIdAndEnabledTrue(projectId);
		return !sources.isEmpty()
				&& sources.stream().allMatch(source -> source.getSyncStatus() == ProjectSourceSyncStatus.SYNCED);
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
