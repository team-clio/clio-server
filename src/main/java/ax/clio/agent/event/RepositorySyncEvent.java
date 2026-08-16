package ax.clio.agent.event;

import ax.clio.agent.client.RepositorySyncRequestType;

/**
 * `project_sources` 변경(등록·제거)을 트랜잭션 커밋 후 Agent로 전달하기 위한 이벤트.
 */
public record RepositorySyncEvent(
		Long projectId,
		Long sourceId,
		RepositorySyncRequestType requestType,
		String branch,
		String sourceUri
) {
}
