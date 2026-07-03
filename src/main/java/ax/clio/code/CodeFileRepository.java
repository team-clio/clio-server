package ax.clio.code;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeFileRepository extends JpaRepository<CodeFile, Long> {

	List<CodeFile> findByProjectIdOrderByPathAsc(Long projectId);

	long countByProjectId(Long projectId);

	void deleteByProjectId(Long projectId);
}
