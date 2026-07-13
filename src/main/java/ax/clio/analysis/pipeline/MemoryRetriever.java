package ax.clio.analysis.pipeline;

import ax.clio.report.entity.BugReport;

/** [파이프라인 포트] 과거 맥락 조회 단계(#8 유사 이슈 + #9 관련 결정). memory 도메인 구현을 감싼다. */
public interface MemoryRetriever {

	MemoryContext retrieve(BugReport report);
}
