package ax.clio.analysis.job.repository;

import ax.clio.analysis.job.entity.AnalysisJob;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {

	List<AnalysisJob> findByReportIdOrderByCreatedAtDesc(Long reportId);
}
