package ax.clio.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import ax.clio.project.entity.Project;
import ax.clio.project.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class IdempotentOperationServiceTest {

	@Autowired
	private IdempotentOperationService service;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AgentOperationRepository operationRepository;

	@Test
	void returnsStoredResponseWithoutRunningOperationAgain() {
		Project project = projectRepository.save(Project.create("Clio", null));
		AtomicInteger executions = new AtomicInteger();
		Map<String, Object> firstPayload = new LinkedHashMap<>();
		firstPayload.put("bugId", 7);
		firstPayload.put("action", "AUTO_LINK");
		Map<String, Object> reorderedPayload = new LinkedHashMap<>();
		reorderedPayload.put("action", "AUTO_LINK");
		reorderedPayload.put("bugId", 7);

		IdempotentResponse<TestResponse> first = service.execute(
				project.getId(),
				AgentOperationType.MATCH_DECISION,
				"REQ-1",
				firstPayload,
				TestResponse.class,
				201,
				() -> new TestResponse(executions.incrementAndGet())
		);
		IdempotentResponse<TestResponse> replay = service.execute(
				project.getId(),
				AgentOperationType.MATCH_DECISION,
				"REQ-1",
				reorderedPayload,
				TestResponse.class,
				201,
				() -> new TestResponse(executions.incrementAndGet())
		);

		assertThat(first.replayed()).isFalse();
		assertThat(replay.replayed()).isTrue();
		assertThat(replay.body().sequence()).isEqualTo(1);
		assertThat(executions).hasValue(1);
		assertThat(operationRepository.count()).isEqualTo(1);
	}

	@Test
	void rejectsSameRequestIdWithDifferentPayload() {
		Project project = projectRepository.save(Project.create("Clio", null));
		service.execute(
				project.getId(),
				AgentOperationType.MATCH_DECISION,
				"REQ-CONFLICT",
				Map.of("bugId", 7),
				TestResponse.class,
				200,
				() -> new TestResponse(1)
		);

		assertThatThrownBy(() -> service.execute(
				project.getId(),
				AgentOperationType.MATCH_DECISION,
				"REQ-CONFLICT",
				Map.of("bugId", 8),
				TestResponse.class,
				200,
				() -> new TestResponse(2)
		)).isInstanceOf(IdempotencyConflictException.class);
	}

	record TestResponse(int sequence) {
	}
}
