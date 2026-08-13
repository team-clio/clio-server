package ax.clio.analysis.repository;

import java.util.Optional;

import ax.clio.analysis.entity.AnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {

	Optional<AnalysisResult> findByWorkflowRunId(Long workflowRunId);

	Optional<AnalysisResult> findFirstByIssueIdOrderByCreatedAtDesc(Long issueId);
}
