package ax.clio.bug.repository;

import java.util.Optional;

import ax.clio.bug.entity.BugOccurrence;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BugOccurrenceRepository extends JpaRepository<BugOccurrence, Long> {

	Optional<BugOccurrence> findByIdAndBugIdAndBugProjectId(Long id, Long bugId, Long projectId);
}
