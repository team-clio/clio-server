package ax.clio.issue.repository;

import java.util.Optional;

import ax.clio.issue.entity.Issue;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueRepository extends JpaRepository<Issue, Long> {

	Optional<Issue> findByIdAndProjectId(Long id, Long projectId);
}
