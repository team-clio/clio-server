package ax.clio.bug.repository;

import java.util.Optional;

import ax.clio.bug.entity.BugSource;
import ax.clio.bug.entity.BugStatus;
import ax.clio.bug.entity.BugOccurrence;
import ax.clio.bug.entity.Severity;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BugOccurrenceRepository extends JpaRepository<BugOccurrence, Long> {

	Optional<BugOccurrence> findByIdAndBugIdAndBugProjectId(Long id, Long bugId, Long projectId);

	Optional<BugOccurrence> findByIdAndProjectId(Long id, Long projectId);

	Optional<BugOccurrence> findFirstByBugIdOrderByOccurredAtDesc(Long bugId);

	@Query("""
			select distinct occurrence
			from BugOccurrence occurrence
			left join occurrence.bug bug
			left join IssueBug issueBug on issueBug.bug = bug
			where occurrence.project.id = :projectId
			  and (:from is null or occurrence.occurredAt >= :from)
			  and (:to is null or occurrence.occurredAt <= :to)
			  and (:issueId is null or issueBug.issue.id = :issueId)
			  and (:source is null or occurrence.source = :source)
			  and (:status is null or bug.status = :status)
			  and (:severity is null or bug.severity = :severity)
			""")
	Page<BugOccurrence> search(
			Long projectId,
			Instant from,
			Instant to,
			Long issueId,
			BugSource source,
			BugStatus status,
			Severity severity,
			Pageable pageable
	);
}
