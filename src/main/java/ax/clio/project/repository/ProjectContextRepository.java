package ax.clio.project.repository;

import java.util.Optional;
import java.util.List;

import ax.clio.project.entity.ProjectContext;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectContextRepository extends JpaRepository<ProjectContext, Long> {

	Optional<ProjectContext> findByProjectIdAndContentHash(Long projectId, String contentHash);

	Optional<ProjectContext> findByIdAndProjectId(Long id, Long projectId);

	List<ProjectContext> findAllByProjectIdOrderByCreatedAtDesc(Long projectId);

	long deleteByProjectId(Long projectId);
}
