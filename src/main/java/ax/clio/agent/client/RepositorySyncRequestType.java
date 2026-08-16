package ax.clio.agent.client;

/**
 * Repository sync 이벤트의 Agent wire 계약(request_type) 이름.
 */
public enum RepositorySyncRequestType {
	REPOSITORY_ADDED("repository_added"),
	REPOSITORY_REMOVED("repository_removed");

	private final String wireName;

	RepositorySyncRequestType(String wireName) {
		this.wireName = wireName;
	}

	public String wireName() {
		return wireName;
	}
}
