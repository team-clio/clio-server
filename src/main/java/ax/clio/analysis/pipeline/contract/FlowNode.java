package ax.clio.analysis.pipeline.contract;

public record FlowNode(
		String filePath,
		String className,
		String role,
		boolean test
) implements java.io.Serializable {

	/** 레이어를 판정할 수 없는 role. 레이어 비교·집계에서 걸러내는 용도. */
	public static final int UNKNOWN_LAYER = Integer.MAX_VALUE;

	/** role 어휘를 레이어 순서로 매핑한다(CONTROLLER 0 → ENTITY 3). 어휘 밖이면 {@link #UNKNOWN_LAYER}. */
	public static int layerOf(String role) {
		if (role == null) {
			return UNKNOWN_LAYER;
		}
		return switch (role) {
			case "CONTROLLER" -> 0;
			case "SERVICE" -> 1;
			case "REPOSITORY" -> 2;
			case "ENTITY" -> 3;
			default -> UNKNOWN_LAYER;
		};
	}

	public int layer() {
		return layerOf(role);
	}
}
