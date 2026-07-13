package ax.clio.memory.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class InMemoryCodeChunkVectorSearchTest {

	private final CodeChunkRepository repository = Mockito.mock(CodeChunkRepository.class);
	private final InMemoryCodeChunkVectorSearch search = new InMemoryCodeChunkVectorSearch(repository);

	@Test
	void ranksByCosineSimilarityDescendingAndLimitsTopK() {
		CodeChunk near = chunk("near", new float[] {1.0f, 0.0f});
		CodeChunk mid = chunk("mid", new float[] {1.0f, 1.0f});
		CodeChunk far = chunk("far", new float[] {0.0f, 1.0f});
		when(repository.findByProjectId(1L)).thenReturn(List.of(mid, far, near));

		List<ScoredCodeChunk> results = search.searchByVector(1L, new float[] {1.0f, 0.0f}, 2);

		assertThat(results).hasSize(2);
		assertThat(results.get(0).chunk().getSymbolName()).isEqualTo("near");
		assertThat(results.get(1).chunk().getSymbolName()).isEqualTo("mid");
		assertThat(results.get(0).score()).isGreaterThan(results.get(1).score());
	}

	@Test
	void skipsEmbeddingsOfDifferentDimensionAndNull() {
		CodeChunk ok = chunk("ok", new float[] {1.0f, 0.0f});
		CodeChunk wrongDim = chunk("wrongDim", new float[] {1.0f, 0.0f, 0.0f});
		CodeChunk noEmbedding = chunk("none", null);
		when(repository.findByProjectId(1L)).thenReturn(List.of(ok, wrongDim, noEmbedding));

		List<ScoredCodeChunk> results = search.searchByVector(1L, new float[] {1.0f, 0.0f}, 10);

		assertThat(results).extracting(scored -> scored.chunk().getSymbolName()).containsExactly("ok");
	}

	@Test
	void emptyQueryReturnsNothing() {
		assertThat(search.searchByVector(1L, new float[] {}, 5)).isEmpty();
	}

	private static CodeChunk chunk(String name, float[] embedding) {
		return new CodeChunk(null, null, "src/A.java", name, null, CodeChunkType.METHOD, 1, 2, "body", embedding);
	}
}
