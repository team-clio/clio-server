package ax.clio.report.repository;

import ax.clio.report.entity.BugReport;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BugReportRepository extends JpaRepository<BugReport, Long> {

	List<BugReport> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}
