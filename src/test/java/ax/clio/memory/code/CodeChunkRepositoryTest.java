package ax.clio.memory.code;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import ax.clio.code.entity.CodeFile;
import ax.clio.project.entity.Project;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/**
 * S1 검증: H2에서 {@code code_chunks} 테이블 DDL(배열 컬럼 포함)이 서고 chunk가 저장·조회된다(D4-1).
 * pgvector 유사도 쿼리 자체는 여기서 검증하지 않는다(D4: 별도 벤치마크).
 */
@DataJpaTest
class CodeChunkRepositoryTest {

	@Autowired
	private CodeChunkRepository codeChunkRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void savesAndReadsBackChunkWithEmbeddingArrayOnH2() {
		Project project = new Project("demo", "/workspace/demo", "desc");
		entityManager.persist(project);
		CodeFile file = new CodeFile(project, "src/A.java", "A.java", "JAVA", false, 100L, Instant.now());
		entityManager.persist(file);

		float[] embedding = {0.1f, 0.2f, 0.3f};
		CodeChunk chunk = new CodeChunk(project, file, "src/A.java", "doThing", "SERVICE",
				CodeChunkType.METHOD, 10, 20, "void doThing() {}", embedding);
		codeChunkRepository.save(chunk);
		entityManager.flush();
		entityManager.clear();

		CodeChunk found = codeChunkRepository.findByProjectId(project.getId()).get(0);
		assertThat(found.getSymbolName()).isEqualTo("doThing");
		assertThat(found.getChunkType()).isEqualTo(CodeChunkType.METHOD);
		assertThat(found.getContent()).isEqualTo("void doThing() {}");
		assertThat(found.getEmbedding()).containsExactly(0.1f, 0.2f, 0.3f);
		assertThat(codeChunkRepository.countByProjectId(project.getId())).isEqualTo(1);
	}
}
