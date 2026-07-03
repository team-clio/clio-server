package ax.clio.analysis;

import java.util.List;

public record StructuredBugReport(
		List<String> keywords,
		List<String> domains,
		IssueType issueType
) {
}
