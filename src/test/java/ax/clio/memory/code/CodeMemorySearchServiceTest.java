package ax.clio.memory.code;

import ax.clio.memory.embedding.EmbeddingClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CodeMemorySearchServiceTest {

	private final EmbeddingClient embeddingClient = Mockito.mock(EmbeddingClient.class);
	private final CodeChunkVectorSearch vectorSearch = Mockito.mock(CodeChunkVectorSearch.class);
	private final CodeMemorySearchService service = new CodeMemorySearchService(embeddingClient, vectorSearch);

	@Test
	void embedsQueryThenDelegatesToVectorSearch() {
		float[] queryVector = {0.1f, 0.2f};
		when(embeddingClient.embed("delete review")).thenReturn(queryVector);
		ScoredCodeChunk hit = new ScoredCodeChunk(null, 0.9);
		when(vectorSearch.searchByVector(1L, queryVector, 5)).thenReturn(List.of(hit));

		List<ScoredCodeChunk> results = service.semanticSearch(1L, "delete review", 5);

		assertThat(results).containsExactly(hit);
	}

	@Test
	void blankQueryShortCircuitsWithoutEmbedding() {
		assertThat(service.semanticSearch(1L, "  ", 5)).isEmpty();
		assertThat(service.semanticSearch(1L, "x", 0)).isEmpty();
		verifyNoInteractions(embeddingClient, vectorSearch);
	}
}
