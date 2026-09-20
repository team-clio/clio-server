package ax.clio.workflow.service;

import java.time.Duration;
import java.util.Locale;
import java.util.function.Supplier;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkflowObservability {
	private static final Logger log = LoggerFactory.getLogger(WorkflowObservability.class);

	private final MeterRegistry meterRegistry;
	private final ObservationRegistry observationRegistry;

	public <T> T observe(String operation, String requestType, Supplier<T> action) {
		return Observation.createNotStarted("clio.workflow.lifecycle", observationRegistry)
				.lowCardinalityKeyValue("operation", operation)
				.lowCardinalityKeyValue("request.type", requestType)
				.observe(action);
	}

	public void recordCreated(String requestType, boolean replayed, Long workflowRunId) {
		String outcome = replayed ? "replayed" : "created";
		meterRegistry.counter("clio.workflow.total", "request.type", requestType, "outcome", outcome)
				.increment();
		if (replayed) {
			meterRegistry.counter("clio.workflow.replay.total", "request.type", requestType).increment();
		}
		log.atInfo().addKeyValue("event", "workflow." + outcome)
				.addKeyValue("request_type", requestType)
				.addKeyValue("workflow_run_id", workflowRunId)
				.log("Workflow request recorded");
	}

	public void recordTransition(
			String requestType,
			String outcome,
			Duration duration,
			String failureCode,
			Long workflowRunId
	) {
		meterRegistry.counter("clio.workflow.total", "request.type", requestType, "outcome", outcome)
				.increment();
		if (duration != null) {
			Timer.builder("clio.workflow.duration")
					.tag("request.type", requestType)
					.tag("outcome", outcome)
					.register(meterRegistry)
					.record(duration);
		}
		if (failureCode != null) {
			meterRegistry.counter(
					"clio.workflow.failure.total",
					"request.type", requestType,
					"error.kind", failureKind(failureCode)
			).increment();
		}
		log.atInfo().addKeyValue("event", "workflow." + outcome)
				.addKeyValue("request_type", requestType)
				.addKeyValue("workflow_run_id", workflowRunId)
				.addKeyValue("error_kind", failureCode == null ? "none" : failureKind(failureCode))
				.log("Workflow status changed");
	}

	public void recordConflict(String requestType) {
		meterRegistry.counter("clio.workflow.request.conflict.total", "request.type", requestType)
				.increment();
		log.atWarn().addKeyValue("event", "workflow.request_conflict")
				.addKeyValue("request_type", requestType).log("Workflow request conflict");
	}

	static String failureKind(String failureCode) {
		String normalized = failureCode.toLowerCase(Locale.ROOT);
		if (normalized.contains("timeout")) {
			return "timeout";
		}
		if (normalized.contains("limit") || normalized.contains("recursion")) {
			return "limit";
		}
		if (normalized.contains("repository") || normalized.contains("server")) {
			return "dependency";
		}
		if (normalized.contains("persist") || normalized.contains("save")) {
			return "persistence";
		}
		if (normalized.contains("validation") || normalized.contains("invalid")) {
			return "validation";
		}
		return "unexpected";
	}
}
