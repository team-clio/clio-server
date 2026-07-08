package ax.clio.analysis;

public record FlowNode(
		String filePath,
		String className,
		String role,
		boolean test
) {
}
