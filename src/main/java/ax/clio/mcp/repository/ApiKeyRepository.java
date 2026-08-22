package ax.clio.mcp.repository;

import ax.clio.mcp.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

	long deleteByProjectId(Long projectId);
}
