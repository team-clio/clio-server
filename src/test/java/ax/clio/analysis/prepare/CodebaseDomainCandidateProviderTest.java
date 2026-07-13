package ax.clio.analysis.prepare;

import ax.clio.analysis.prepare.CodebaseDomainCandidateProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import ax.clio.code.entity.CodeFile;
import ax.clio.code.entity.CodeSymbol;
import ax.clio.code.repository.CodeSymbolRepository;
import ax.clio.code.entity.CodeSymbolType;
import ax.clio.project.entity.Project;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CodebaseDomainCandidateProviderTest {

	private final CodeSymbolRepository repository = Mockito.mock(CodeSymbolRepository.class);
	private final CodebaseDomainCandidateProvider provider = new CodebaseDomainCandidateProvider(repository);

	@Test
	void usesOrderedSymbolLookupAndLimitsCandidateCount() {
		List<CodeSymbol> symbols = new ArrayList<>();
		for (int index = 0; index < 90; index++) {
			symbols.add(symbol("Domain" + index + "Service", "SERVICE"));
		}
		when(repository.findByProjectIdOrderByFilePathAscStartLineAsc(1L)).thenReturn(symbols);

		List<String> candidates = provider.findCandidates(1L);

		assertThat(candidates).hasSize(80);
		assertThat(candidates.get(0)).isEqualTo("Domain0");
		assertThat(candidates.get(79)).isEqualTo("Domain79");
		verify(repository).findByProjectIdOrderByFilePathAscStartLineAsc(1L);
	}

	@Test
	void stripsTechnicalSuffixesAndFiltersNonDomainClasses() {
		when(repository.findByProjectIdOrderByFilePathAscStartLineAsc(1L)).thenReturn(List.of(
				symbol("PaymentService", "SERVICE"),
				symbol("OrderRepository", "REPOSITORY"),
				symbol("InternalConfig", null),
				symbol("OrderStatus", null, CodeSymbolType.ENUM, "ax.clio.order")
		));

		List<String> candidates = provider.findCandidates(1L);

		assertThat(candidates).containsExactly("Payment", "Order", "OrderStatus");
	}

	private CodeSymbol symbol(String name, String role) {
		return symbol(name, role, CodeSymbolType.CLASS, "ax.clio.payment.service");
	}

	private CodeSymbol symbol(String name, String role, CodeSymbolType type, String packageName) {
		Project project = new Project("shop", "/workspace/shop", "shop service");
		CodeFile file = new CodeFile(project, "src/main/java/" + name + ".java", name + ".java", "JAVA", false, 100,
				Instant.now());
		return new CodeSymbol(project, file, name, type, role, packageName, 1, 10, "", "");
	}
}
