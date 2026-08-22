package ax.clio.analysis.repository;

import java.util.Optional;

import ax.clio.analysis.entity.AnalysisResult;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {

	Optional<AnalysisResult> findByWorkflowRunId(Long workflowRunId);

	Optional<AnalysisResult> findFirstByIssueIdOrderByCreatedAtDesc(Long issueId);

	@Modifying
	@Query("update AnalysisResult result set result.previousAnalysisResult = null where result.workflowRun.project.id = :projectId")
	void clearPreviousAnalysisResultByProjectId(@Param("projectId") Long projectId);

	long deleteByWorkflowRunProjectId(Long projectId);
}
