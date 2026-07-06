package ax.clio.llm;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmConfigRepository extends JpaRepository<LlmConfig, Long> {

	List<LlmConfig> findAllByOrderByDefaultConfigDescNameAsc();

	Optional<LlmConfig> findByDefaultConfigTrueAndEnabledTrue();

	boolean existsByDefaultConfigTrue();
}
