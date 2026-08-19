package ax.clio.agent.client;

public enum DocumentSyncRequestType {
	DOCUMENT_ADDED("document_added"),
	DOCUMENT_DELETED("document_deleted");

	private final String wireName;

	DocumentSyncRequestType(String wireName) {
		this.wireName = wireName;
	}

	public String wireName() {
		return wireName;
	}
}
