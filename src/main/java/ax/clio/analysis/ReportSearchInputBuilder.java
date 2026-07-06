package ax.clio.analysis;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import ax.clio.report.BugReport;
import org.springframework.stereotype.Component;

@Component
public class ReportSearchInputBuilder {

	public List<ReportSearchInput> build(BugReport report, ReportSearchPreparation preparation) {
		return build(report, preparation, ReportSearchInputMode.HYBRID);
	}

	public List<ReportSearchInput> build(BugReport report, ReportSearchPreparation preparation, ReportSearchInputMode mode) {
		Set<ReportSearchInput> inputs = new LinkedHashSet<>();
		if (mode == ReportSearchInputMode.RAW_ONLY || mode == ReportSearchInputMode.HYBRID) {
			add(inputs, report.getTitle(), ReportSearchInputType.RAW_REPORT);
			add(inputs, report.getDescription(), ReportSearchInputType.RAW_REPORT);
		}
		if (mode == ReportSearchInputMode.PREPARED_ONLY || mode == ReportSearchInputMode.HYBRID) {
			preparation.candidateDomains().forEach(domain -> add(inputs, domain, ReportSearchInputType.CANDIDATE_DOMAIN));
			preparation.businessTerms().forEach(keyword -> add(inputs, keyword, ReportSearchInputType.KEYWORD));
			preparation.codeSearchTerms().forEach(keyword -> add(inputs, keyword, ReportSearchInputType.KEYWORD));
		}
		return List.copyOf(inputs);
	}

	private void add(Set<ReportSearchInput> inputs, String query, ReportSearchInputType type) {
		if (query == null || query.isBlank()) {
			return;
		}
		inputs.add(new ReportSearchInput(query.strip(), type));
	}
}
