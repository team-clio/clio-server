package ax.clio.code.entity;

public record CodeScanResult(
		Long projectId,
		long fileCount,
		long symbolCount
) {
}
