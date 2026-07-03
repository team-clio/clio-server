package ax.clio.code;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeSymbolRepository extends JpaRepository<CodeSymbol, Long> {

	List<CodeSymbol> findByProjectIdOrderByFilePathAscStartLineAsc(Long projectId);

	List<CodeSymbol> findByProjectId(Long projectId);

	long countByProjectId(Long projectId);

	void deleteByProjectId(Long projectId);
}
