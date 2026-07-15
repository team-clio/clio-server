package ax.clio.analysis.pipeline.port;

import ax.clio.analysis.pipeline.contract.CodeFlow;
import ax.clio.analysis.pipeline.contract.RankedCodeCandidate;

import java.util.List;

/** [파이프라인 포트] 영향 흐름 추적 단계(Controller→Service→Repository). */
public interface FlowAnalyzer {

	List<CodeFlow> trace(Long projectId, List<RankedCodeCandidate> candidates);
}
