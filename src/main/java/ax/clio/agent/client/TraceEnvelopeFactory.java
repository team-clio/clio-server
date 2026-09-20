package ax.clio.agent.client;

import java.util.Map;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TraceEnvelopeFactory {
	private final Tracer tracer;

	Map<String, String> current() {
		Span span = tracer.currentSpan();
		if (span == null) {
			return Map.of();
		}
		TraceContext context = span.context();
		String flags = Boolean.TRUE.equals(context.sampled()) ? "01" : "00";
		return Map.of("traceparent", "00-" + context.traceId() + "-" + context.spanId() + "-" + flags);
	}
}
