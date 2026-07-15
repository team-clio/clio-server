package ax.clio.analysis.flow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ax.clio.analysis.pipeline.contract.FlowNode;
import ax.clio.code.entity.CodeSymbol;
import ax.clio.code.entity.CodeSymbolType;

/**
 * 프로젝트 심볼로부터 클래스 단위 의존 그래프를 구성한다(순수 로직, 스프링 의존 없음).
 *
 * <p>노드 = 클래스 레벨 심볼(CLASS/INTERFACE/RECORD/ENUM), key = FQN(packageName.name).
 * 엣지 A→B = A의 import 선언에 B의 FQN이 정확히 등장하면 추가(프로젝트 내부 노드끼리만).
 * import는 클래스 단위이므로 흐름은 "레이어" 수준이다(메서드 콜그래프 아님).
 */
public class CodeDependencyGraph {

	private final Map<String, Node> nodesByFqn = new LinkedHashMap<>();
	private final Map<String, Node> nodesByFilePath = new HashMap<>();
	private final Map<String, Set<String>> dependencies = new HashMap<>(); // A → {B...}
	private final Map<String, Set<String>> dependents = new HashMap<>();    // B → {A...}

	public CodeDependencyGraph(List<CodeSymbol> symbols) {
		for (CodeSymbol symbol : symbols) {
			if (!isClassLevel(symbol.getType())) {
				continue;
			}
			String fqn = fqnOf(symbol.getPackageName(), symbol.getName());
			Node node = new Node(
					fqn,
					symbol.getFile().getPath(),
					symbol.getName(),
					symbol.getRole(),
					symbol.getFile().isTest(),
					parseImports(symbol.getImports()));
			nodesByFqn.putIfAbsent(fqn, node);
			registerFilePath(node);
		}
		buildEdges();
	}

	private void registerFilePath(Node node) {
		Node existing = nodesByFilePath.get(node.filePath());
		// 파일명과 클래스명이 일치하는 최상위 클래스를 대표로 우선한다.
		if (existing == null || isPrimaryForFile(node)) {
			nodesByFilePath.put(node.filePath(), node);
		}
	}

	private boolean isPrimaryForFile(Node node) {
		String fileName = node.filePath().substring(node.filePath().lastIndexOf('/') + 1);
		if (fileName.endsWith(".java")) {
			fileName = fileName.substring(0, fileName.length() - ".java".length());
		}
		return fileName.equals(node.className());
	}

	private void buildEdges() {
		for (Node node : nodesByFqn.values()) {
			for (String imported : node.imports()) {
				Node target = nodesByFqn.get(imported);
				if (target == null || target.fqn().equals(node.fqn())) {
					continue;
				}
				dependencies.computeIfAbsent(node.fqn(), k -> new HashSet<>()).add(target.fqn());
				dependents.computeIfAbsent(target.fqn(), k -> new HashSet<>()).add(node.fqn());
			}
		}
	}

	public Node nodeByFilePath(String filePath) {
		return nodesByFilePath.get(filePath);
	}

	public Node nodeByFqn(String fqn) {
		return nodesByFqn.get(fqn);
	}

	/** A가 의존하는 노드들(A → B, 하류). */
	public Set<String> dependenciesOf(String fqn) {
		return Collections.unmodifiableSet(dependencies.getOrDefault(fqn, Set.of()));
	}

	/** A에 의존하는 노드들(B ← A, 상류 호출자). */
	public Set<String> dependentsOf(String fqn) {
		return Collections.unmodifiableSet(dependents.getOrDefault(fqn, Set.of()));
	}

	private static boolean isClassLevel(CodeSymbolType type) {
		return type == CodeSymbolType.CLASS
				|| type == CodeSymbolType.INTERFACE
				|| type == CodeSymbolType.RECORD
				|| type == CodeSymbolType.ENUM;
	}

	private static String fqnOf(String packageName, String name) {
		if (packageName == null || packageName.isBlank()) {
			return name;
		}
		return packageName + "." + name;
	}

	/**
	 * import 선언 문자열(개행 구분)을 FQN 집합으로 파싱한다.
	 * "import ax.clio.Foo;" → "ax.clio.Foo". static import와 와일드카드는 정확 매칭이 안 되므로 제외.
	 */
	private static Set<String> parseImports(String imports) {
		if (imports == null || imports.isBlank()) {
			return Set.of();
		}
		Set<String> result = new HashSet<>();
		for (String line : imports.split("\\R")) {
			String token = line.trim();
			if (token.isEmpty()) {
				continue;
			}
			if (token.startsWith("import")) {
				token = token.substring("import".length()).trim();
			}
			if (token.startsWith("static")) {
				continue; // 멤버 static import는 클래스 FQN과 정확 매칭되지 않음
			}
			if (token.endsWith(";")) {
				token = token.substring(0, token.length() - 1).trim();
			}
			if (token.isEmpty() || token.endsWith("*")) {
				continue; // 와일드카드는 초기 범위 밖
			}
			result.add(token);
		}
		return result;
	}

	/** 그래프 노드. */
	public record Node(
			String fqn,
			String filePath,
			String className,
			String role,
			boolean test,
			Set<String> imports
	) {
		public int layer() {
			return FlowNode.layerOf(role);
		}
	}

	List<Node> allNodes() {
		return new ArrayList<>(nodesByFqn.values());
	}
}
