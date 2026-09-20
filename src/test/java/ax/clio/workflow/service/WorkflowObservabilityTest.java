package ax.clio.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

class WorkflowObservabilityTest {

	@Test
	void recordsLifecycleOutcomeAndDurationWithoutRequestIdentifiers() {
		SimpleMeterRegistry meters = new SimpleMeterRegistry();
		WorkflowObservability observability = new WorkflowObservability(
				meters,
				ObservationRegistry.create()
		);

		observability.recordCreated("analyze_issue", false, 17L);
		observability.recordTransition(
				"analyze_issue",
				"completed",
				Duration.ofSeconds(12),
				null,
				17L
		);

		assertThat(meters.counter(
				"clio.workflow.total",
				"request.type", "analyze_issue",
				"outcome", "created"
		).count()).isEqualTo(1.0);
		assertThat(meters.timer(
				"clio.workflow.duration",
				"request.type", "analyze_issue",
				"outcome", "completed"
		).totalTime(java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(12.0);
	}

	@Test
	void mapsFailureCodesToBoundedKinds() {
		assertThat(WorkflowObservability.failureKind("MODEL_CALL_LIMIT")).isEqualTo("limit");
		assertThat(WorkflowObservability.failureKind("SERVER_TIMEOUT")).isEqualTo("timeout");
		assertThat(WorkflowObservability.failureKind("arbitrary.Exception")).isEqualTo("unexpected");
	}
}
