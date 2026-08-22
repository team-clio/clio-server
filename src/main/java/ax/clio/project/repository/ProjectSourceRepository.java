package ax.clio.project.repository;

import java.util.List;
import java.util.Optional;

import ax.clio.project.entity.ProjectSource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectSourceRepository extends JpaRepository<ProjectSource, Long> {

	List<ProjectSource> findAllByProjectIdOrderByCreatedAtAsc(Long projectId);

	List<ProjectSource> findAllByProjectIdAndEnabledTrue(Long projectId);

	Optional<ProjectSource> findByIdAndProjectId(Long id, Long projectId);

	boolean existsByProjectIdAndRepoUrl(Long projectId, String repoUrl);

	boolean existsByProjectIdAndRepoUrlAndIdNot(Long projectId, String repoUrl, Long id);

	long deleteByProjectId(Long projectId);
}
