package ax.clio.analysis.flow;

import ax.clio.analysis.pipeline.CodeFlow;
import ax.clio.analysis.pipeline.FlowNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ax.clio.code.entity.CodeSymbol;
import ax.clio.code.repository.CodeSymbolRepository;
import org.springframework.stereotype.Component;

/**
 * 랭킹된 코드 후보를 시드로, 의존 그래프를 따라 관련 흐름(CodeFlow)을 확장한다.
 *
 * <p>노이즈를 줄이기 위해 확장 노드는 role이 있는(레이어에 속한) 노드만 흐름에 포함한다.
 * 시드 노드는 role과 무관하게 포함한다.
 */
@Component
public class FlowTracer {

	static final int DEFAULT_MAX_DEPTH = 2;
	static final int DEFAULT_MAX_FLOWS = 5;

	private final CodeSymbolRepository codeSymbolRepository;

	public FlowTracer(CodeSymbolRepository codeSymbolRepository) {
		this.codeSymbolRepository = codeSymbolRepository;
	}

	public List<CodeFlow> trace(Long projectId, List<String> candidateFilePaths) {
		return trace(projectId, candidateFilePaths, DEFAULT_MAX_DEPTH, DEFAULT_MAX_FLOWS);
	}

	public List<CodeFlow> trace(Long projectId, List<String> candidateFilePaths, int maxDepth, int maxFlows) {
		if (candidateFilePaths == null || candidateFilePaths.isEmpty()) {
			return List.of();
		}
		List<CodeSymbol> symbols = codeSymbolRepository.findByProjectId(projectId);
		CodeDependencyGraph graph = new CodeDependencyGraph(symbols);

		// 후보 → 시드 노드 매핑(중복 제거, 매칭 실패는 skip)
		List<CodeDependencyGraph.Node> seeds = new ArrayList<>();
		Set<String> seenSeedFqn = new LinkedHashSet<>();
		for (String path : candidateFilePaths) {
			CodeDependencyGraph.Node node = graph.nodeByFilePath(path);
			if (node != null && seenSeedFqn.add(node.fqn())) {
				seeds.add(node);
			}
		}
		if (seeds.isEmpty()) {
			return List.of();
		}

		// 각 시드의 bounded 이웃 집합 계산
		Map<String, Set<String>> reachableBySeed = new HashMap<>();
		for (CodeDependencyGraph.Node seed : seeds) {
			reachableBySeed.put(seed.fqn(), expand(graph, seed, maxDepth));
		}

		// 이웃이 겹치는 시드끼리 union-find로 병합
		UnionFind unionFind = new UnionFind(seeds.stream().map(CodeDependencyGraph.Node::fqn).toList());
		for (int i = 0; i < seeds.size(); i++) {
			for (int j = i + 1; j < seeds.size(); j++) {
				String a = seeds.get(i).fqn();
				String b = seeds.get(j).fqn();
				if (overlaps(reachableBySeed.get(a), reachableBySeed.get(b))) {
					unionFind.union(a, b);
				}
			}
		}

		// 그룹별 흐름 노드 수집 → 레이어 정렬
		Map<String, Set<String>> groupNodes = new HashMap<>();
		for (CodeDependencyGraph.Node seed : seeds) {
			String root = unionFind.find(seed.fqn());
			groupNodes.computeIfAbsent(root, k -> new LinkedHashSet<>())
					.addAll(reachableBySeed.get(seed.fqn()));
		}

		List<CodeFlow> flows = new ArrayList<>();
		for (Set<String> fqns : groupNodes.values()) {
			List<FlowNode> flowNodes = fqns.stream()
					.map(graph::nodeByFqn)
					.filter(n -> n != null)
					.sorted(Comparator.comparingInt(CodeDependencyGraph.Node::layer)
							.thenComparing(CodeDependencyGraph.Node::className))
					.map(n -> new FlowNode(n.filePath(), n.className(), n.role(), n.test()))
					.toList();
			if (!flowNodes.isEmpty()) {
				flows.add(new CodeFlow(flowNodes));
			}
		}

		// 노드가 많은(넓은) 흐름 우선, 상한 적용
		return flows.stream()
				.sorted(Comparator.comparingInt((CodeFlow f) -> f.nodes().size()).reversed()
						.thenComparing(CodeFlow::describe))
				.limit(maxFlows)
				.toList();
	}

	/**
	 * 시드에서 상/하류로 depth 제한까지 방문한다. 흐름에 포함하는 노드는
	 * 시드이거나 role이 있는(레이어에 속한) 노드로 한정해 노이즈를 줄인다.
	 */
	private Set<String> expand(CodeDependencyGraph graph, CodeDependencyGraph.Node seed, int maxDepth) {
		Set<String> included = new LinkedHashSet<>();
		included.add(seed.fqn());

		Set<String> visited = new LinkedHashSet<>();
		visited.add(seed.fqn());
		Deque<String> frontier = new ArrayDeque<>();
		frontier.add(seed.fqn());

		for (int depth = 0; depth < maxDepth && !frontier.isEmpty(); depth++) {
			Deque<String> next = new ArrayDeque<>();
			while (!frontier.isEmpty()) {
				String current = frontier.poll();
				Set<String> neighbors = new LinkedHashSet<>();
				neighbors.addAll(graph.dependenciesOf(current));
				neighbors.addAll(graph.dependentsOf(current));
				for (String neighbor : neighbors) {
					if (!visited.add(neighbor)) {
						continue;
					}
					CodeDependencyGraph.Node node = graph.nodeByFqn(neighbor);
					if (node != null && node.role() != null) {
						included.add(neighbor);
					}
					next.add(neighbor);
				}
			}
			frontier = next;
		}
		return included;
	}

	private static boolean overlaps(Set<String> a, Set<String> b) {
		Set<String> smaller = a.size() <= b.size() ? a : b;
		Set<String> larger = smaller == a ? b : a;
		for (String element : smaller) {
			if (larger.contains(element)) {
				return true;
			}
		}
		return false;
	}

	/** 시드 병합용 최소 union-find. */
	private static final class UnionFind {
		private final Map<String, String> parent = new HashMap<>();

		UnionFind(List<String> elements) {
			for (String element : elements) {
				parent.put(element, element);
			}
		}

		String find(String x) {
			String root = x;
			while (!root.equals(parent.get(root))) {
				root = parent.get(root);
			}
			// 경로 압축
			String current = x;
			while (!current.equals(root)) {
				String nextNode = parent.get(current);
				parent.put(current, root);
				current = nextNode;
			}
			return root;
		}

		void union(String a, String b) {
			parent.put(find(a), find(b));
		}
	}
}
