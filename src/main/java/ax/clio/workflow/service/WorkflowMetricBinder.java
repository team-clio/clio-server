package ax.clio.workflow.service;

import java.time.Duration;
import java.time.Instant;

import ax.clio.workflow.entity.AgentWorkflowStatus;
import ax.clio.workflow.repository.AgentWorkflowRunRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkflowMetricBinder implements MeterBinder {
	private final AgentWorkflowRunRepository workflowRunRepository;

	@Value("${clio.observability.stuck-threshold:PT5M}")
	private Duration stuckThreshold;

	@Override
	public void bindTo(MeterRegistry registry) {
		Gauge.builder(
				"clio.workflow.in.progress",
				workflowRunRepository,
				repository -> repository.countByStatus(AgentWorkflowStatus.RUNNING)
		).register(registry);
		Gauge.builder(
				"clio.workflow.stuck",
				workflowRunRepository,
				repository -> repository.countByStatusAndStartedAtBefore(
						AgentWorkflowStatus.RUNNING,
						Instant.now().minus(stuckThreshold)
				)
		).register(registry);
		Gauge.builder(
				"clio.workflow.running.age.max",
				workflowRunRepository,
				repository -> repository
						.findFirstByStatusAndStartedAtIsNotNullOrderByStartedAtAsc(AgentWorkflowStatus.RUNNING)
						.map(run -> Duration.between(run.getStartedAt(), Instant.now()).toSeconds())
						.orElse(0L)
		).baseUnit("seconds").register(registry);
	}
}
