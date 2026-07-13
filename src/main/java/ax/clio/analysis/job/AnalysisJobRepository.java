package ax.clio.analysis.job;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {

	List<AnalysisJob> findByReportIdOrderByCreatedAtDesc(Long reportId);
}
