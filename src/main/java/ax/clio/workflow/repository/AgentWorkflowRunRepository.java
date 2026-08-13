package ax.clio.workflow.repository;

import java.util.Optional;

import ax.clio.workflow.entity.AgentWorkflowRun;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AgentWorkflowRunRepository extends JpaRepository<AgentWorkflowRun, Long> {

	Optional<AgentWorkflowRun> findByProjectIdAndRequestId(Long projectId, String requestId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<AgentWorkflowRun> findByIdAndProjectId(Long id, Long projectId);
}
