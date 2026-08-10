package ax.clio.analysis.repository;

import java.util.Optional;

import ax.clio.analysis.entity.AnalysisJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {

	Optional<AnalysisJob> findByIdAndProjectId(Long id, Long projectId);

	Optional<AnalysisJob> findByIdAndProjectIdAndIssueId(Long id, Long projectId, Long issueId);
}
