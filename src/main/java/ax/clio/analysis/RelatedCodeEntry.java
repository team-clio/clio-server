package ax.clio.analysis;

public record RelatedCodeEntry(
		String filePath,
		String symbolName,
		String symbolRole,
		String matchType,
		Integer lineNumber,
		String snippet,
		int score,
		int hitCount
) {
}
