package ax.clio.memory.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LocalEmbeddingClientTest {

	private final LocalEmbeddingClient client = new LocalEmbeddingClient();

	@Test
	void isDeterministicAndUnitLength() {
		float[] a = client.embed("public void deleteReview(Long id)");
		float[] b = client.embed("public void deleteReview(Long id)");

		assertThat(a).containsExactly(b);
		assertThat(a).hasSize(client.dimensions());
		assertThat(norm(a)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
	}

	@Test
	void handlesKoreanTokensNotJustAscii() {
		// 버그 리포트는 한글 → 한글 토큰이 살아야 함(비ASCII 처리)
		float[] korean = client.embed("결제 취소 오류");
		assertThat(norm(korean)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));

		float[] query = client.embed("결제 취소");
		float[] related = client.embed("결제 취소가 안됨");
		float[] unrelated = client.embed("로그인 세션 만료");
		assertThat(cosine(query, related)).isGreaterThan(cosine(query, unrelated));
	}

	@Test
	void emptyTextYieldsZeroVector() {
		assertThat(norm(client.embed(""))).isZero();
		assertThat(norm(client.embed(null))).isZero();
	}

	@Test
	void sharedTokensAreMoreSimilarThanDisjoint() {
		float[] query = client.embed("delete review");
		float[] overlapping = client.embed("public void deleteReview()");
		float[] disjoint = client.embed("calculate tax invoice total");

		assertThat(cosine(query, overlapping)).isGreaterThan(cosine(query, disjoint));
	}

	private static double norm(float[] v) {
		double s = 0.0;
		for (float x : v) {
			s += (double) x * x;
		}
		return Math.sqrt(s);
	}

	private static double cosine(float[] a, float[] b) {
		double dot = 0.0;
		for (int i = 0; i < a.length; i++) {
			dot += (double) a[i] * b[i];
		}
		return dot; // 둘 다 단위벡터이므로 dot == cosine
	}
}
