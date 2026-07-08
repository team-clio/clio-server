package ax.clio.code;

public record CodeSearchResult(
		Long fileId,
		String filePath,
		String symbolName,
		CodeSymbolType symbolType,
		String symbolRole,
		CodeSearchMatchType matchType,
		Integer lineNumber,
		String snippet,
		int score,
		boolean test
) {
}
