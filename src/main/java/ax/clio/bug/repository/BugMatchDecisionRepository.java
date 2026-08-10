package ax.clio.bug.repository;

import ax.clio.bug.entity.BugMatchDecision;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BugMatchDecisionRepository extends JpaRepository<BugMatchDecision, Long> {
}
