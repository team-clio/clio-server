package ax.clio.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import ax.clio.code.CodeFile;
import ax.clio.code.CodeSymbol;
import ax.clio.code.CodeSymbolType;
import ax.clio.project.Project;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/**
 * 전 구간 통합(S2+S3+S4+S5): 스캔 인제스트(chunk→embed→저장) 후 semanticSearch가 관련 chunk를 상위로 돌려준다.
 * 로컬 embedding + in-memory 코사인으로 pgvector 없이 H2에서 배선을 실증한다(D4: CI는 기능 검증).
 */
@DataJpaTest
class CodeMemoryIndexAndSearchIntegrationTest {

	@Autowired
	private CodeChunkRepository codeChunkRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void indexesChunksAndSemanticSearchFindsRelevantMethod() {
		Project project = new Project("demo", "/workspace/demo", "desc");
		entityManager.persist(project);
		CodeFile file = new CodeFile(project, "src/ReviewService.java", "ReviewService.java", "JAVA", false, 0L,
				Instant.now());
		entityManager.persist(file);
		entityManager.flush();

		CodeChunkIndexer indexer = new CodeChunkIndexer(new CodeChunker(), new LocalEmbeddingClient(),
				codeChunkRepository);
		CodeMemorySearchService searchService = new CodeMemorySearchService(new LocalEmbeddingClient(),
				new InMemoryCodeChunkVectorSearch(codeChunkRepository));

		List<String> lines = List.of(
				"package a;",                              // 1
				"public class ReviewService {",           // 2
				"  public void deleteReview(Long id) {",  // 3
				"    reviews.remove(id);",                // 4
				"  }",                                    // 5
				"  public void calculateTax(Order o) {",  // 6
				"    tax.compute(o);",                    // 7
				"  }",                                    // 8
				"}"                                       // 9
		);
		List<CodeSymbol> symbols = List.of(
				new CodeSymbol(project, file, "ReviewService", CodeSymbolType.CLASS, "SERVICE", "a", 2, 9, "", ""),
				new CodeSymbol(project, file, "deleteReview", CodeSymbolType.METHOD, null, "a", 3, 5, "", ""),
				new CodeSymbol(project, file, "calculateTax", CodeSymbolType.METHOD, null, "a", 6, 8, "", "")
		);

		indexer.index(project, file, lines, symbols);
		entityManager.flush();
		entityManager.clear();

		assertThat(codeChunkRepository.countByProjectId(project.getId())).isEqualTo(3);

		List<ScoredCodeChunk> results = searchService.semanticSearch(project.getId(), "delete review", 1);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).chunk().getSymbolName()).isEqualTo("deleteReview");
	}
}
