package ax.clio.memory;

import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * 결정적 로컬 embedding (외부 의존 0). 토큰 해싱(feature hashing) + L2 정규화로 고정차원 벡터를 만든다.
 *
 * <p><b>한계(D3):</b> 이것은 "같은 토큰이 겹치는가"를 벡터로 표현할 뿐 <b>의미 검색이 아니다.</b>
 * "명사 자체가 다른 코드 찾기"(#7의 진짜 목표)는 로컬로는 원리적으로 불가. 이 구현이 검증하는 것은
 * chunk→embed→저장→코사인 top-k→fallback <b>배선이 돈다</b>는 것뿐이다. 실제 semantic 값은 API 구현(옵션)에서.
 *
 * <p>코드 친화적으로 camelCase/snake_case 경계와 비영숫자를 토큰 분리한다.
 */
@Component
public class LocalEmbeddingClient implements EmbeddingClient {

	private static final int DIMENSIONS = 256;

	@Override
	public int dimensions() {
		return DIMENSIONS;
	}

	@Override
	public float[] embed(String text) {
		float[] vector = new float[DIMENSIONS];
		if (text == null || text.isBlank()) {
			return vector;
		}
		for (String token : tokenize(text)) {
			if (token.isEmpty()) {
				continue;
			}
			int hash = token.hashCode();
			int index = Math.floorMod(hash, DIMENSIONS);
			int sign = (Integer.rotateLeft(hash, 1) & 1) == 0 ? 1 : -1;
			vector[index] += sign;
		}
		return normalize(vector);
	}

	private static String[] tokenize(String text) {
		// camelCase 경계에 공백 삽입 후, 유니코드 문자/숫자가 아닌 것으로 분리, 소문자화.
		// (한글 등 비ASCII도 토큰으로 살린다 — 버그 리포트가 한글이므로 필수.)
		String spaced = text.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
		return spaced.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+");
	}

	private static float[] normalize(float[] vector) {
		double sumSquares = 0.0;
		for (float value : vector) {
			sumSquares += (double) value * value;
		}
		if (sumSquares == 0.0) {
			return vector;
		}
		float norm = (float) Math.sqrt(sumSquares);
		for (int i = 0; i < vector.length; i++) {
			vector[i] /= norm;
		}
		return vector;
	}
}
