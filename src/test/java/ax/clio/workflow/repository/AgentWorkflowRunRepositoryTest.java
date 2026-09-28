package ax.clio.workflow.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

class AgentWorkflowRunRepositoryTest {

	@Test
	void appliesWriteLockOnlyToMutationLookup() throws NoSuchMethodException {
		Method readLookup = AgentWorkflowRunRepository.class
				.getMethod("findByIdAndProjectId", Long.class, Long.class);
		Method updateLookup = AgentWorkflowRunRepository.class
				.getMethod("findByIdAndProjectIdForUpdate", Long.class, Long.class);

		assertThat(readLookup.getAnnotation(Lock.class)).isNull();
		assertThat(updateLookup.getAnnotation(Lock.class).value())
				.isEqualTo(LockModeType.PESSIMISTIC_WRITE);
	}
}
