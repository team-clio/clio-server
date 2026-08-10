package ax.clio.issue.repository;

import java.util.List;
import java.util.Optional;

import ax.clio.issue.entity.IssueBug;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueBugRepository extends JpaRepository<IssueBug, Long> {

	Optional<IssueBug> findByBugId(Long bugId);

	boolean existsByIssueIdAndBugId(Long issueId, Long bugId);

	List<IssueBug> findByIssueIdOrderByBugOccurrenceCountDesc(Long issueId);
}
