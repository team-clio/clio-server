package ax.clio.agent.event;

/**
 * 커밋된 버그를 Agent에 보낼 시점을 알린다. {@code createNewIssue}가 true면 매칭 없이 신규 Issue로 처리한다.
 */
public record BugCollectedEvent(Long projectId, Long bugId, boolean createNewIssue) {

	public BugCollectedEvent(Long projectId, Long bugId) {
		this(projectId, bugId, false);
	}
}
