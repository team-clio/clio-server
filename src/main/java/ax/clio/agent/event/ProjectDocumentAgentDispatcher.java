package ax.clio.agent.event;

import java.util.Map;

import ax.clio.agent.client.ClioAgentClient;
import ax.clio.agent.client.ClioAgentProperties;
import ax.clio.agent.client.DocumentSyncRequestType;
import ax.clio.project.service.ProjectDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectDocumentAgentDispatcher {

	private final ClioAgentClient agentClient;
	private final ClioAgentProperties agentProperties;
	private final ProjectDocumentService projectDocumentService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void dispatch(ProjectDocumentSyncEvent event) {
		if (!agentProperties.enabled()) {
			if (event.requestType() == DocumentSyncRequestType.DOCUMENT_DELETED) {
				projectDocumentService.completeDocumentDeletion(event.projectId(), event.documentId());
			}
			return;
		}
		try {
			if (event.requestType() == DocumentSyncRequestType.DOCUMENT_ADDED) {
				projectDocumentService.markDocumentSyncing(event.projectId(), event.documentId());
			}
			agentClient.syncDocument(
					event.projectId(), event.documentId(), event.requestType(), event.title(), event.markdown(),
					event.requestType() == DocumentSyncRequestType.DOCUMENT_ADDED
							? Map.of("media_type", event.mediaType(), "original_filename", event.originalFilename())
							: Map.of()
			);
			if (event.requestType() == DocumentSyncRequestType.DOCUMENT_ADDED) {
				projectDocumentService.markDocumentSynced(event.projectId(), event.documentId());
			} else {
				projectDocumentService.completeDocumentDeletion(event.projectId(), event.documentId());
			}
		} catch (RuntimeException exception) {
			log.error("Failed to synchronize project document. projectId={}, documentId={}", event.projectId(), event.documentId(), exception);
			projectDocumentService.markDocumentSyncFailed(event.projectId(), event.documentId());
		}
	}
}
