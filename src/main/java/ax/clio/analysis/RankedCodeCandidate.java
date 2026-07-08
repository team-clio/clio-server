package ax.clio.analysis;

import java.util.List;

import ax.clio.code.CodeSearchMatchType;
import ax.clio.code.CodeSymbolType;

public record RankedCodeCandidate(
		Long fileId,
		String filePath,
		String symbolName,
		CodeSymbolType symbolType,
		String symbolRole,
		CodeSearchMatchType matchType,
		Integer lineNumber,
		String snippet,
		boolean test,
		int baseScore,
		int adjustedScore,
		int hitCount,
		List<ReportSearchInputType> matchedInputTypes
) {
}
