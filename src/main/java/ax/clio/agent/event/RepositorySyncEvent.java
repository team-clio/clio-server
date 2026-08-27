package ax.clio.agent.event;

import java.util.List;

import ax.clio.agent.client.RepositorySyncRequestType;

/**
 * `project_sources` 변경(등록·제거)을 트랜잭션 커밋 후 Agent로 전달하기 위한 이벤트.
 */
public record RepositorySyncEvent(
		Long projectId,
		Long sourceId,
		RepositorySyncRequestType requestType,
		String requestId,
		String branch,
		String sourceUri,
		List<String> includePaths,
		List<String> excludePaths
) {
}
