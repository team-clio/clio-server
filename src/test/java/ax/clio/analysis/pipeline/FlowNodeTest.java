package ax.clio.analysis.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FlowNodeTest {

	@Test
	void layerOfMapsRolesInOrder() {
		assertThat(FlowNode.layerOf("CONTROLLER")).isEqualTo(0);
		assertThat(FlowNode.layerOf("SERVICE")).isEqualTo(1);
		assertThat(FlowNode.layerOf("REPOSITORY")).isEqualTo(2);
		assertThat(FlowNode.layerOf("ENTITY")).isEqualTo(3);
	}

	@Test
	void layerOfReturnsUnknownForNullOrUnmappedRole() {
		assertThat(FlowNode.layerOf(null)).isEqualTo(FlowNode.UNKNOWN_LAYER);
		assertThat(FlowNode.layerOf("SOMETHING_ELSE")).isEqualTo(FlowNode.UNKNOWN_LAYER);
	}

	@Test
	void layerReadsOwnRole() {
		assertThat(new FlowNode("a/B.java", "B", "SERVICE", false).layer()).isEqualTo(1);
		assertThat(new FlowNode("a/B.java", "B", null, false).layer()).isEqualTo(FlowNode.UNKNOWN_LAYER);
	}
}
