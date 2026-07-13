package ax.clio.memory.code;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import ax.clio.code.entity.CodeFile;
import ax.clio.code.entity.CodeSymbol;
import ax.clio.code.entity.CodeSymbolType;
import ax.clio.project.entity.Project;
import org.junit.jupiter.api.Test;

class CodeChunkerTest {

	private final CodeChunker chunker = new CodeChunker();

	@Test
	void splitsMethodsAndClassHeaderInheritingRole() {
		Project project = new Project("demo", "/workspace/demo", "desc");
		CodeFile file = new CodeFile(project, "src/FooService.java", "FooService.java", "JAVA", false, 0L, Instant.now());

		List<String> lines = List.of(
				"package a;",                                        // 1
				"@Service",                                          // 2
				"public class FooService {",                         // 3
				"  private final Repo repo;",                        // 4
				"  public FooService(Repo repo) { this.repo = repo; }", // 5
				"  public void doThing() {",                         // 6
				"    repo.save();",                                  // 7
				"  }",                                               // 8
				"}"                                                  // 9
		);

		List<CodeSymbol> symbols = List.of(
				symbol(project, file, "FooService", CodeSymbolType.CLASS, "SERVICE", 2, 9),
				symbol(project, file, "repo", CodeSymbolType.FIELD, null, 4, 4),
				symbol(project, file, "FooService", CodeSymbolType.CONSTRUCTOR, null, 5, 5),
				symbol(project, file, "doThing", CodeSymbolType.METHOD, null, 6, 8)
		);

		List<CodeChunk> chunks = chunker.chunk(project, file, lines, symbols);

		assertThat(chunks).hasSize(3);

		CodeChunk header = chunks.stream()
				.filter(chunk -> chunk.getChunkType() == CodeChunkType.CLASS_HEADER)
				.findFirst().orElseThrow();
		assertThat(header.getSymbolName()).isEqualTo("FooService");
		assertThat(header.getRole()).isEqualTo("SERVICE");
		assertThat(header.getContent()).contains("class FooService", "private final Repo repo");
		assertThat(header.getContent()).doesNotContain("doThing");

		CodeChunk method = chunks.stream()
				.filter(chunk -> "doThing".equals(chunk.getSymbolName()))
				.findFirst().orElseThrow();
		assertThat(method.getChunkType()).isEqualTo(CodeChunkType.METHOD);
		assertThat(method.getRole()).isEqualTo("SERVICE");
		assertThat(method.getContent()).contains("repo.save();");

		assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getEmbedding()).isNull());
	}

	private CodeSymbol symbol(Project project, CodeFile file, String name, CodeSymbolType type, String role,
			int startLine, int endLine) {
		return new CodeSymbol(project, file, name, type, role, "a", startLine, endLine, "", "");
	}
}
