package ax.clio.memory;

import java.util.List;

import ax.clio.code.CodeFile;
import ax.clio.code.CodeSymbol;
import ax.clio.project.Project;
import org.springframework.stereotype.Component;

/**
 * 스캔 시 한 파일의 심볼을 chunk로 만들고(embed 포함) 저장한다 (S2 생성 + S4 embedding 부착).
 * D7: 스캔 시 전체 재생성 — {@link #deleteByProjectId}로 기존 chunk를 지우고 다시 만든다.
 */
@Component
public class CodeChunkIndexer {

	private final CodeChunker codeChunker;
	private final EmbeddingClient embeddingClient;
	private final CodeChunkRepository codeChunkRepository;

	public CodeChunkIndexer(CodeChunker codeChunker, EmbeddingClient embeddingClient,
			CodeChunkRepository codeChunkRepository) {
		this.codeChunker = codeChunker;
		this.embeddingClient = embeddingClient;
		this.codeChunkRepository = codeChunkRepository;
	}

	public void deleteByProjectId(Long projectId) {
		codeChunkRepository.deleteByProjectId(projectId);
	}

	public void index(Project project, CodeFile file, List<String> sourceLines, List<CodeSymbol> symbols) {
		List<CodeChunk> chunks = codeChunker.chunk(project, file, sourceLines, symbols);
		for (CodeChunk chunk : chunks) {
			chunk.assignEmbedding(embeddingClient.embed(embeddingText(chunk)));
		}
		codeChunkRepository.saveAll(chunks);
	}

	/** embedding 입력 텍스트: 심볼 이름 + 본문(선언 포함). 실제 API에선 이름이 추가 신호가 된다. */
	private static String embeddingText(CodeChunk chunk) {
		String name = chunk.getSymbolName() != null ? chunk.getSymbolName() : "";
		String content = chunk.getContent() != null ? chunk.getContent() : "";
		return (name + "\n" + content).strip();
	}
}
