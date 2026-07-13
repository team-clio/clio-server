package ax.clio.analysis.flow;

import ax.clio.analysis.flow.FlowTracer;
import ax.clio.analysis.pipeline.CodeFlow;
import ax.clio.analysis.pipeline.FlowNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import ax.clio.code.CodeFile;
import ax.clio.code.CodeSymbol;
import ax.clio.code.CodeSymbolRepository;
import ax.clio.code.CodeSymbolType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlowTracerTest {

	private CodeSymbolRepository codeSymbolRepository;
	private FlowTracer flowTracer;

	@BeforeEach
	void setUp() {
		codeSymbolRepository = mock(CodeSymbolRepository.class);
		flowTracer = new FlowTracer(codeSymbolRepository);
	}

	@Test
	void controllerServiceRepositoryFormOneFlowSortedByLayer() {
		stubSymbols(
				classSymbol("payment/PaymentController.java", "PaymentController", "ax.clio.payment", "CONTROLLER", false,
						"import ax.clio.payment.PaymentService;"),
				classSymbol("payment/PaymentService.java", "PaymentService", "ax.clio.payment", "SERVICE", false,
						"import ax.clio.payment.PaymentRepository;"),
				classSymbol("payment/PaymentRepository.java", "PaymentRepository", "ax.clio.payment", "REPOSITORY", false));

		List<CodeFlow> flows = flowTracer.trace(1L, List.of("payment/PaymentController.java"));

		assertThat(flows).hasSize(1);
		assertThat(flows.getFirst().describe())
				.isEqualTo("PaymentController -> PaymentService -> PaymentRepository");
	}

	@Test
	void unrelatedNodeNotIncludedInFlow() {
		stubSymbols(
				classSymbol("payment/PaymentController.java", "PaymentController", "ax.clio.payment", "CONTROLLER", false,
						"import ax.clio.payment.PaymentService;"),
				classSymbol("payment/PaymentService.java", "PaymentService", "ax.clio.payment", "SERVICE", false),
				classSymbol("order/OrderController.java", "OrderController", "ax.clio.order", "CONTROLLER", false));

		List<CodeFlow> flows = flowTracer.trace(1L, List.of("payment/PaymentController.java"));

		assertThat(flows).hasSize(1);
		assertThat(flows.getFirst().nodes()).extracting(FlowNode::className)
				.containsExactly("PaymentController", "PaymentService")
				.doesNotContain("OrderController");
	}

	@Test
	void rolelessExpandedNodeIsExcluded() {
		stubSymbols(
				classSymbol("payment/PaymentService.java", "PaymentService", "ax.clio.payment", "SERVICE", false,
						"import ax.clio.util.StringUtil;"),
				classSymbol("util/StringUtil.java", "StringUtil", "ax.clio.util", null, false));

		List<CodeFlow> flows = flowTracer.trace(1L, List.of("payment/PaymentService.java"));

		assertThat(flows).hasSize(1);
		assertThat(flows.getFirst().nodes()).extracting(FlowNode::className)
				.containsExactly("PaymentService")
				.doesNotContain("StringUtil");
	}

	@Test
	void twoConnectedSeedsMergeIntoOneFlow() {
		stubSymbols(
				classSymbol("payment/PaymentController.java", "PaymentController", "ax.clio.payment", "CONTROLLER", false,
						"import ax.clio.payment.PaymentService;"),
				classSymbol("payment/PaymentService.java", "PaymentService", "ax.clio.payment", "SERVICE", false,
						"import ax.clio.payment.PaymentRepository;"),
				classSymbol("payment/PaymentRepository.java", "PaymentRepository", "ax.clio.payment", "REPOSITORY", false));

		List<CodeFlow> flows = flowTracer.trace(1L,
				List.of("payment/PaymentController.java", "payment/PaymentRepository.java"));

		assertThat(flows).hasSize(1);
		assertThat(flows.getFirst().nodes()).hasSize(3);
	}

	@Test
	void depthLimitStopsExpansion() {
		// A -> B -> C -> D, seed A, depth 2 → D 제외
		stubSymbols(
				classSymbol("a/A.java", "A", "ax.clio.a", "CONTROLLER", false, "import ax.clio.b.B;"),
				classSymbol("b/B.java", "B", "ax.clio.b", "SERVICE", false, "import ax.clio.c.C;"),
				classSymbol("c/C.java", "C", "ax.clio.c", "SERVICE", false, "import ax.clio.d.D;"),
				classSymbol("d/D.java", "D", "ax.clio.d", "REPOSITORY", false));

		List<CodeFlow> flows = flowTracer.trace(1L, List.of("a/A.java"), 2, 5);

		assertThat(flows.getFirst().nodes()).extracting(FlowNode::className)
				.containsExactly("A", "B", "C")
				.doesNotContain("D");
	}

	@Test
	void unmatchedCandidatePathReturnsEmpty() {
		stubSymbols(classSymbol("payment/PaymentService.java", "PaymentService", "ax.clio.payment", "SERVICE", false));

		assertThat(flowTracer.trace(1L, List.of("nowhere/Missing.java"))).isEmpty();
	}

	@Test
	void emptyCandidatesReturnEmpty() {
		assertThat(flowTracer.trace(1L, List.of())).isEmpty();
	}

	private void stubSymbols(CodeSymbol... symbols) {
		when(codeSymbolRepository.findByProjectId(eq(1L))).thenReturn(List.of(symbols));
	}

	private static CodeSymbol classSymbol(String path, String name, String pkg, String role, boolean test,
			String... importLines) {
		CodeFile file = mock(CodeFile.class);
		when(file.getPath()).thenReturn(path);
		when(file.isTest()).thenReturn(test);

		CodeSymbol symbol = mock(CodeSymbol.class);
		when(symbol.getFile()).thenReturn(file);
		when(symbol.getName()).thenReturn(name);
		when(symbol.getType()).thenReturn(CodeSymbolType.CLASS);
		when(symbol.getPackageName()).thenReturn(pkg);
		when(symbol.getRole()).thenReturn(role);
		when(symbol.getImports()).thenReturn(String.join("\n", importLines));
		return symbol;
	}
}
