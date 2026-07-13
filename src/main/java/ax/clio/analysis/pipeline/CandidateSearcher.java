package ax.clio.analysis.pipeline;

import java.util.List;

import ax.clio.report.entity.BugReport;

/** [파이프라인 포트] 관련 코드 검색 단계. 검색 입력 구성 + 랭킹을 캡슐화한다. */
public interface CandidateSearcher {

	List<RankedCodeCandidate> search(BugReport report, ReportSearchPreparation preparation, ReportSearchInputMode mode);
}
