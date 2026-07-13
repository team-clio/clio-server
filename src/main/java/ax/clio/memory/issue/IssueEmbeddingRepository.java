package ax.clio.memory.issue;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueEmbeddingRepository extends JpaRepository<IssueEmbedding, Long> {

	List<IssueEmbedding> findByProjectId(Long projectId);

	long countByProjectId(Long projectId);

	void deleteByReportId(Long reportId);

	void deleteByProjectId(Long projectId);
}
