package ax.clio.project.repository;

import ax.clio.project.entity.RepositoryCredential;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositoryCredentialRepository extends JpaRepository<RepositoryCredential, Long> {

	void deleteByProjectSourceId(Long projectSourceId);
}
