package ax.clio.bug.repository;

import ax.clio.bug.entity.BugGroupingDecision;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BugGroupingDecisionRepository extends JpaRepository<BugGroupingDecision, Long> {
}
