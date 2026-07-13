package ax.clio.analysis;

import java.util.List;

/** [파이프라인 포트] 영향 흐름 추적 단계(Controller→Service→Repository). */
public interface FlowAnalyzer {

	List<CodeFlow> trace(Long projectId, List<RankedCodeCandidate> candidates);
}
