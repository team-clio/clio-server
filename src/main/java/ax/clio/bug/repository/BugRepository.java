package ax.clio.bug.repository;

import java.util.Optional;

import ax.clio.bug.entity.Bug;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BugRepository extends JpaRepository<Bug, Long> {

	Optional<Bug> findByIdAndProjectId(Long id, Long projectId);
}
