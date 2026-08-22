package ax.clio.issue.repository;

import ax.clio.issue.entity.IssueBranch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueBranchRepository extends JpaRepository<IssueBranch, Long> {

	long deleteByIssueProjectId(Long projectId);
}
