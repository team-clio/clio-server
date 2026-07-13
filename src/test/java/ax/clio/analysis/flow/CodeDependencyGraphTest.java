package ax.clio.analysis.flow;

import ax.clio.analysis.flow.CodeDependencyGraph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import ax.clio.code.entity.CodeFile;
import ax.clio.code.entity.CodeSymbol;
import ax.clio.code.entity.CodeSymbolType;
import org.junit.jupiter.api.Test;

class CodeDependencyGraphTest {

	@Test
	void importEdgeCreatedBetweenInternalNodes() {
		CodeDependencyGraph graph = new CodeDependencyGraph(List.of(
				classSymbol("payment/PaymentService.java", "PaymentService", "ax.clio.payment", "SERVICE", false,
						"import ax.clio.payment.PaymentRepository;"),
				classSymbol("payment/PaymentRepository.java", "PaymentRepository", "ax.clio.payment", "REPOSITORY", false)));

		assertThat(graph.dependenciesOf("ax.clio.payment.PaymentService"))
				.containsExactly("ax.clio.payment.PaymentRepository");
		assertThat(graph.dependentsOf("ax.clio.payment.PaymentRepository"))
				.containsExactly("ax.clio.payment.PaymentService");
	}

	@Test
	void externalImportProducesNoEdge() {
		CodeDependencyGraph graph = new CodeDependencyGraph(List.of(
				classSymbol("payment/PaymentService.java", "PaymentService", "ax.clio.payment", "SERVICE", false,
						"import java.util.List;", "import org.springframework.stereotype.Service;")));

		assertThat(graph.dependenciesOf("ax.clio.payment.PaymentService")).isEmpty();
	}

	@Test
	void staticAndWildcardImportsAreSkipped() {
		CodeDependencyGraph graph = new CodeDependencyGraph(List.of(
				classSymbol("a/A.java", "A", "ax.clio.a", null, false,
						"import static ax.clio.b.B.value;", "import ax.clio.b.*;"),
				classSymbol("b/B.java", "B", "ax.clio.b", null, false)));

		assertThat(graph.dependenciesOf("ax.clio.a.A")).isEmpty();
	}

	@Test
	void onlyClassLevelSymbolsBecomeNodes() {
		CodeDependencyGraph graph = new CodeDependencyGraph(List.of(
				classSymbol("payment/PaymentService.java", "PaymentService", "ax.clio.payment", "SERVICE", false),
				methodSymbol("payment/PaymentService.java", "processPayment", "ax.clio.payment")));

		assertThat(graph.nodeByFqn("ax.clio.payment.PaymentService")).isNotNull();
		assertThat(graph.nodeByFqn("ax.clio.payment.processPayment")).isNull();
	}

	@Test
	void nodeLookupByFilePath() {
		CodeDependencyGraph graph = new CodeDependencyGraph(List.of(
				classSymbol("payment/PaymentService.java", "PaymentService", "ax.clio.payment", "SERVICE", false)));

		CodeDependencyGraph.Node node = graph.nodeByFilePath("payment/PaymentService.java");
		assertThat(node).isNotNull();
		assertThat(node.className()).isEqualTo("PaymentService");
		assertThat(node.layer()).isEqualTo(1);
	}

	@Test
	void layerOfMapsRolesInOrder() {
		assertThat(CodeDependencyGraph.layerOf("CONTROLLER")).isEqualTo(0);
		assertThat(CodeDependencyGraph.layerOf("SERVICE")).isEqualTo(1);
		assertThat(CodeDependencyGraph.layerOf("REPOSITORY")).isEqualTo(2);
		assertThat(CodeDependencyGraph.layerOf("ENTITY")).isEqualTo(3);
		assertThat(CodeDependencyGraph.layerOf(null)).isEqualTo(CodeDependencyGraph.UNKNOWN_LAYER);
	}

	private static CodeSymbol classSymbol(String path, String name, String pkg, String role, boolean test,
			String... importLines) {
		return symbol(path, name, CodeSymbolType.CLASS, pkg, role, test, importLines);
	}

	private static CodeSymbol methodSymbol(String path, String name, String pkg) {
		return symbol(path, name, CodeSymbolType.METHOD, pkg, null, false);
	}

	private static CodeSymbol symbol(String path, String name, CodeSymbolType type, String pkg, String role,
			boolean test, String... importLines) {
		CodeFile file = mock(CodeFile.class);
		when(file.getPath()).thenReturn(path);
		when(file.isTest()).thenReturn(test);

		CodeSymbol symbol = mock(CodeSymbol.class);
		when(symbol.getFile()).thenReturn(file);
		when(symbol.getName()).thenReturn(name);
		when(symbol.getType()).thenReturn(type);
		when(symbol.getPackageName()).thenReturn(pkg);
		when(symbol.getRole()).thenReturn(role);
		when(symbol.getImports()).thenReturn(String.join("\n", importLines));
		return symbol;
	}
}
