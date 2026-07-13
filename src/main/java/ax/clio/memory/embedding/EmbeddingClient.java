package ax.clio.memory.embedding;

import java.util.List;

/**
 * 텍스트를 embedding 벡터로 변환하는 범용 substrate (D0-B: 코드 chunk 전용이 아니라 임의 텍스트 재사용 가능).
 *
 * <p>구현체:
 * <ul>
 *   <li>{@link LocalEmbeddingClient} — 결정적 로컬(외부 의존 0). 기본/CI. <b>의미검색이 아니라 배선 검증용</b>(D3).</li>
 *   <li>OpenAI호환 API 구현 — 설정 시 실제 semantic. (자동 선택 배선은 D3-1 후속.)</li>
 * </ul>
 */
public interface EmbeddingClient {

	/** 이 클라이언트가 만드는 벡터의 차원. 한 클라이언트가 만든 벡터끼리만 유사도 비교가 유효하다. */
	int dimensions();

	float[] embed(String text);

	default List<float[]> embedAll(List<String> texts) {
		return texts.stream().map(this::embed).toList();
	}
}
