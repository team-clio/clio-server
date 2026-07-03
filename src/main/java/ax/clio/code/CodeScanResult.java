package ax.clio.code;

public record CodeScanResult(
		Long projectId,
		long fileCount,
		long symbolCount
) {
}
