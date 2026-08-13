package ax.clio.agent.event;

public record BugCollectedEvent(Long projectId, Long bugId) {
}
