package ax.clio.common.idempotency;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentOperationRepository extends JpaRepository<AgentOperation, Long> {

	Optional<AgentOperation> findByProjectIdAndOperationTypeAndRequestId(
			Long projectId,
			AgentOperationType operationType,
			String requestId
	);
}
