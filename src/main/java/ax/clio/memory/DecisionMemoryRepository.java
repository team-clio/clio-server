package ax.clio.memory;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionMemoryRepository extends JpaRepository<DecisionMemory, Long> {

	List<DecisionMemory> findByProjectId(Long projectId);

	List<DecisionMemory> findByProjectIdOrderByCreatedAtDesc(Long projectId);

	long countByProjectId(Long projectId);

	void deleteByProjectId(Long projectId);
}
