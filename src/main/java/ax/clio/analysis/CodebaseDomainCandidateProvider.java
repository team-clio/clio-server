package ax.clio.analysis;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import ax.clio.code.CodeSymbol;
import ax.clio.code.CodeSymbolRepository;
import ax.clio.code.CodeSymbolType;
import org.springframework.stereotype.Component;

@Component
public class CodebaseDomainCandidateProvider {

	private static final List<String> TECHNICAL_SUFFIXES = List.of(
			"Controller",
			"Service",
			"Repository",
			"Entity",
			"Handler",
			"Manager",
			"Client",
			"Config",
			"Configuration",
			"Request",
			"Response",
			"Dto"
	);

	private final CodeSymbolRepository codeSymbolRepository;

	public CodebaseDomainCandidateProvider(CodeSymbolRepository codeSymbolRepository) {
		this.codeSymbolRepository = codeSymbolRepository;
	}

	public List<String> findCandidates(Long projectId) {
		Set<String> candidates = new LinkedHashSet<>();
		for (CodeSymbol symbol : codeSymbolRepository.findByProjectId(projectId)) {
			if (!isDomainCandidate(symbol)) {
				continue;
			}
			String baseName = stripTechnicalSuffix(symbol.getName());
			if (baseName.length() >= 3) {
				candidates.add(baseName);
			}
		}
		return List.copyOf(candidates);
	}

	private boolean isDomainCandidate(CodeSymbol symbol) {
		if (symbol.getType() == CodeSymbolType.ENUM || symbol.getType() == CodeSymbolType.RECORD) {
			return true;
		}
		if (symbol.getType() != CodeSymbolType.CLASS && symbol.getType() != CodeSymbolType.INTERFACE) {
			return false;
		}
		String role = symbol.getRole();
		if (role != null) {
			return role.equals("ENTITY") || role.equals("SERVICE") || role.equals("REPOSITORY");
		}
		String packageName = symbol.getPackageName() == null ? "" : symbol.getPackageName();
		return packageName.contains(".domain.")
				|| packageName.endsWith(".domain")
				|| packageName.contains(".model.")
				|| packageName.endsWith(".model")
				|| packageName.contains(".entity.")
				|| packageName.endsWith(".entity");
	}

	private String stripTechnicalSuffix(String name) {
		for (String suffix : TECHNICAL_SUFFIXES) {
			if (name.endsWith(suffix) && name.length() > suffix.length()) {
				return name.substring(0, name.length() - suffix.length());
			}
		}
		return name;
	}
}
