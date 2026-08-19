package ax.clio.agent.event;

import ax.clio.agent.client.DocumentSyncRequestType;

public record ProjectDocumentSyncEvent(
		Long projectId,
		Long documentId,
		DocumentSyncRequestType requestType,
		String title,
		String markdown,
		String mediaType,
		String originalFilename
) {
}
