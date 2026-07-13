package ax.clio.analysis.pipeline;

public record FlowNode(
		String filePath,
		String className,
		String role,
		boolean test
) implements java.io.Serializable {
}
