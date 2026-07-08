package ax.clio.analysis;

import java.util.List;

/**
 * 하나의 영향 흐름. 노드는 레이어 오름차순(CONTROLLER→SERVICE→REPOSITORY→ENTITY→UNKNOWN)으로
 * 정렬되어 있다. 예: PaymentController -> PaymentService -> PaymentRepository.
 */
public record CodeFlow(
		List<FlowNode> nodes
) {

	/** 흐름을 "A -> B -> C" 형태의 한 줄로 표현한다(className 기준). */
	public String describe() {
		return String.join(" -> ", nodes.stream().map(FlowNode::className).toList());
	}
}
