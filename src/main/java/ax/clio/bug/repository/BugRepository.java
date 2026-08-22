package ax.clio.bug.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import ax.clio.bug.entity.Bug;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;

public interface BugRepository extends JpaRepository<Bug, Long>, JpaSpecificationExecutor<Bug> {

	Optional<Bug> findByIdAndProjectId(Long id, Long projectId);

	@Query("""
			select bug.id from Bug bug
			where bug.project.id = :projectId and bug.status = :status
			order by bug.id
			""")
	List<Long> findIdsByProjectIdAndStatusOrderByIdAsc(Long projectId, ax.clio.bug.entity.BugStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select bug from Bug bug where bug.id = :id and bug.project.id = :projectId")
	Optional<Bug> findByIdAndProjectIdForUpdate(Long id, Long projectId);

	List<Bug> findByProjectIdAndIdGreaterThanOrderByIdAsc(Long projectId, Long id, Pageable pageable);

	@Query("""
			select bug
			from Bug bug
			join IssueBug issueBug on issueBug.bug = bug
			where issueBug.issue.project.id = :projectId
			order by bug.occurredAt
			""")
	List<Bug> findLinkedForStats(Long projectId);

	@Query("""
			select bug
			from Bug bug
			join IssueBug issueBug on issueBug.bug = bug
			where issueBug.issue.project.id = :projectId
			  and bug.occurredAt >= :from
			order by bug.occurredAt
			""")
	List<Bug> findLinkedForStatsFrom(Long projectId, Instant from);

	@Query("""
			select bug
			from Bug bug
			join IssueBug issueBug on issueBug.bug = bug
			where issueBug.issue.project.id = :projectId
			  and bug.occurredAt <= :to
			order by bug.occurredAt
			""")
	List<Bug> findLinkedForStatsTo(Long projectId, Instant to);

	@Query("""
			select bug
			from Bug bug
			join IssueBug issueBug on issueBug.bug = bug
			where issueBug.issue.project.id = :projectId
			  and bug.occurredAt between :from and :to
			order by bug.occurredAt
			""")
	List<Bug> findLinkedForStatsBetween(Long projectId, Instant from, Instant to);

	long deleteByProjectId(Long projectId);
}
