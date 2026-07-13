package ax.clio.analysis.search;

import ax.clio.analysis.pipeline.CandidateSearcher;
import ax.clio.analysis.pipeline.RankedCodeCandidate;
import ax.clio.analysis.pipeline.ReportSearchInput;
import ax.clio.analysis.pipeline.ReportSearchInputMode;
import ax.clio.analysis.pipeline.ReportSearchPreparation;

import java.util.List;

import ax.clio.report.BugReport;
import org.springframework.stereotype.Component;

/** 검색 입력 구성 + 랭킹을 결합한 {@link CandidateSearcher} 구현. */
@Component
class DefaultCandidateSearcher implements CandidateSearcher {

	private final ReportSearchInputBuilder searchInputBuilder;
	private final CodeCandidateRanker codeCandidateRanker;

	DefaultCandidateSearcher(ReportSearchInputBuilder searchInputBuilder, CodeCandidateRanker codeCandidateRanker) {
		this.searchInputBuilder = searchInputBuilder;
		this.codeCandidateRanker = codeCandidateRanker;
	}

	@Override
	public List<RankedCodeCandidate> search(BugReport report, ReportSearchPreparation preparation,
			ReportSearchInputMode mode) {
		List<ReportSearchInput> searchInputs = searchInputBuilder.build(report, preparation, mode);
		return codeCandidateRanker.rank(report.getProject().getId(), searchInputs);
	}
}
