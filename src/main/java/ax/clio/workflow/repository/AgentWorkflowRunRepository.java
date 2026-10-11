package ax.clio.workflow.repository;

import java.time.Instant;
import java.util.Optional;

import ax.clio.workflow.entity.AgentWorkflowRun;
import ax.clio.workflow.entity.AgentWorkflowStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentWorkflowRunRepository extends JpaRepository<AgentWorkflowRun, Long> {

	Optional<AgentWorkflowRun> findByProjectIdAndRequestId(Long projectId, String requestId);

	Optional<AgentWorkflowRun> findByIdAndProjectId(Long id, Long projectId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select run from AgentWorkflowRun run where run.id = :id and run.project.id = :projectId")
	Optional<AgentWorkflowRun> findByIdAndProjectIdForUpdate(
			@Param("id") Long id,
			@Param("projectId") Long projectId
	);

	@Query("""
			select count(run) from AgentWorkflowRun run
			where run.project.id = :projectId
			  and (run.requestId = :requestId or run.requestId like concat(:requestId, '-retry-%'))
			""")
	long countAttempts(@Param("projectId") Long projectId, @Param("requestId") String requestId);

	@Query("""
			select run from AgentWorkflowRun run
			where run.project.id = :projectId
			  and (run.requestId = :requestId or run.requestId like concat(:requestId, '-retry-%'))
			order by run.id desc
			""")
	java.util.List<AgentWorkflowRun> findAttemptsLatestFirst(
			@Param("projectId") Long projectId,
			@Param("requestId") String requestId
	);

	long countByStatus(AgentWorkflowStatus status);

	long countByStatusAndStartedAtBefore(AgentWorkflowStatus status, Instant startedAt);

	Optional<AgentWorkflowRun> findFirstByStatusAndStartedAtIsNotNullOrderByStartedAtAsc(
			AgentWorkflowStatus status
	);

	long deleteByProjectId(Long projectId);
}
