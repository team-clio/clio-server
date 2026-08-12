package ax.clio.project.repository;

import java.util.List;
import java.util.Optional;

import ax.clio.project.entity.Project;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {

	List<Project> findAllByOrderByNameAsc();

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select project from Project project where project.id = :projectId")
	Optional<Project> findByIdForUpdate(@Param("projectId") Long projectId);
}
