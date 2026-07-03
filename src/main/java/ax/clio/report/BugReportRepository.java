package ax.clio.report;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BugReportRepository extends JpaRepository<BugReport, Long> {

	List<BugReport> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}
