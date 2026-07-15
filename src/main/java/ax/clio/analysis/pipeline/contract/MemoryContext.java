package ax.clio.analysis.pipeline.contract;

import java.io.Serializable;
import java.util.List;

/** 과거 맥락(유사 이슈·관련 결정) 계약. 파이프라인 단계 간 전달·체크포인트 직렬화 대상. */
public record MemoryContext(
		List<SimilarIssueEntry> similarIssues,
		List<RelatedDecisionEntry> relatedDecisions
) implements Serializable {
}
