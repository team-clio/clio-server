package ax.clio.workflow.repository;

import java.time.Instant;
import java.util.Optional;

import ax.clio.workflow.entity.AgentWorkflowRun;
import ax.clio.workflow.entity.AgentWorkflowStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AgentWorkflowRunRepository extends JpaRepository<AgentWorkflowRun, Long> {

	Optional<AgentWorkflowRun> findByProjectIdAndRequestId(Long projectId, String requestId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<AgentWorkflowRun> findByIdAndProjectId(Long id, Long projectId);

	long countByStatus(AgentWorkflowStatus status);

	long countByStatusAndStartedAtBefore(AgentWorkflowStatus status, Instant startedAt);

	Optional<AgentWorkflowRun> findFirstByStatusAndStartedAtIsNotNullOrderByStartedAtAsc(
			AgentWorkflowStatus status
	);

	long deleteByProjectId(Long projectId);
}
