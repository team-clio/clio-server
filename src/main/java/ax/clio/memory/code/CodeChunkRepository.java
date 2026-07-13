package ax.clio.memory.code;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeChunkRepository extends JpaRepository<CodeChunk, Long> {

	List<CodeChunk> findByProjectId(Long projectId);

	long countByProjectId(Long projectId);

	void deleteByProjectId(Long projectId);
}
