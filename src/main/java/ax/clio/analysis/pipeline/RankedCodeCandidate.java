package ax.clio.analysis.pipeline;

import java.util.List;

import ax.clio.code.entity.CodeSearchMatchType;
import ax.clio.code.entity.CodeSymbolType;

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
) implements java.io.Serializable {
}
