package ax.clio.analysis.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;

/**
 * #12의 핵심 기능(실패 단계부터 재개, G6/G7-3)이 우리가 의존하는 langgraph4j + MemorySaver로 실제 동작함을
 * 최소 그래프로 검증한다. 노드 b가 처음엔 실패하고 재실행 때 성공하며, 이미 성공한 노드 a는 재실행되지 않는다.
 */
class GraphResumeTest {

	static class CountState extends AgentState {
		CountState(Map<String, Object> initData) {
			super(initData);
		}
	}

	@Test
	void resumesFromFailedNodeWithoutRerunningCompletedNode() throws Exception {
		AtomicInteger aRuns = new AtomicInteger();
		AtomicInteger bRuns = new AtomicInteger();
		AtomicInteger bFailUntil = new AtomicInteger(1); // 첫 호출만 실패

		var graph = new StateGraph<CountState>(Map.of(), CountState::new)
				.addNode("a", node_async(state -> {
					aRuns.incrementAndGet();
					return Map.of("a", "done");
				}))
				.addNode("b", node_async(state -> {
					if (bRuns.getAndIncrement() < bFailUntil.get()) {
						throw new IllegalStateException("b failed");
					}
					return Map.of("b", "done");
				}))
				.addEdge(START, "a")
				.addEdge("a", "b")
				.addEdge("b", END);

		MemorySaver saver = new MemorySaver();
		CompiledGraph<CountState> compiled = graph.compile(
				CompileConfig.builder().checkpointSaver(saver).build());

		RunnableConfig config = RunnableConfig.builder().threadId("job-1").build();

		// 1차: a 성공 → b 실패
		try {
			compiled.invoke(Map.of(), config);
		} catch (Exception expected) {
			// b가 던진 실패
		}
		assertThat(aRuns.get()).isEqualTo(1);
		assertThat(bRuns.get()).isEqualTo(1);

		// 2차: 같은 threadId로 재개(입력 null = 저장 체크포인트에서 이어감) → a는 재실행 안 됨, b만 재실행
		var result = compiled.invoke(null, config);

		assertThat(result).isPresent();
		assertThat(aRuns.get()).isEqualTo(1); // a는 여전히 1회 (재개로 건너뜀)
		assertThat(bRuns.get()).isEqualTo(2); // b만 재실행
	}
}
