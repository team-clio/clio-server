package ax.clio.memory.code;

import ax.clio.memory.embedding.EmbeddingClient;

import ax.clio.analysis.search.CodeCandidateRanker;

import java.util.List;

import org.springframework.stereotype.Service;

/**
 * 의미(semantic) 검색 서비스 (S5). 쿼리 텍스트 → embedding → 벡터 유사도 top-k.
 *
 * <p>D6: 범용 substrate로 노출(향후 MCP 툴·#8 재사용) + {@code CodeCandidateRanker} fallback이 호출.
 * D0-A: "필요할 때 부르는 도구" — 항상 블렌딩하지 않는다.
 */
@Service
public class CodeMemorySearchService {

	private final EmbeddingClient embeddingClient;
	private final CodeChunkVectorSearch codeChunkVectorSearch;

	public CodeMemorySearchService(EmbeddingClient embeddingClient, CodeChunkVectorSearch codeChunkVectorSearch) {
		this.embeddingClient = embeddingClient;
		this.codeChunkVectorSearch = codeChunkVectorSearch;
	}

	public List<ScoredCodeChunk> semanticSearch(Long projectId, String query, int topK) {
		if (query == null || query.isBlank() || topK <= 0) {
			return List.of();
		}
		float[] queryVector = embeddingClient.embed(query);
		return codeChunkVectorSearch.searchByVector(projectId, queryVector, topK);
	}
}
