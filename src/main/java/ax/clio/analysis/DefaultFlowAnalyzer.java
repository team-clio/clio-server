package ax.clio.analysis;

import java.util.List;

import org.springframework.stereotype.Component;

/** 후보 파일 경로 추출 + 흐름 추적을 결합한 {@link FlowAnalyzer} 구현. */
@Component
class DefaultFlowAnalyzer implements FlowAnalyzer {

	private final FlowTracer flowTracer;

	DefaultFlowAnalyzer(FlowTracer flowTracer) {
		this.flowTracer = flowTracer;
	}

	@Override
	public List<CodeFlow> trace(Long projectId, List<RankedCodeCandidate> candidates) {
		List<String> candidatePaths = candidates.stream()
				.map(RankedCodeCandidate::filePath).distinct().toList();
		return flowTracer.trace(projectId, candidatePaths);
	}
}
