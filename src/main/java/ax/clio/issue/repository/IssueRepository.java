package ax.clio.issue.repository;

import java.util.Optional;

import ax.clio.issue.entity.Issue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface IssueRepository extends JpaRepository<Issue, Long>, JpaSpecificationExecutor<Issue> {

	Optional<Issue> findByIdAndProjectId(Long id, Long projectId);

	boolean existsByIdAndProjectId(Long id, Long projectId);
}
