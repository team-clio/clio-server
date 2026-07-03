package ax.clio.code;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import ax.clio.common.BusinessException;
import ax.clio.project.Project;
import ax.clio.project.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CodeAnalysisService {

	private final ProjectService projectService;
	private final CodeScanner codeScanner;
	private final JavaCodeIndexer javaCodeIndexer;
	private final CodeFileRepository codeFileRepository;
	private final CodeSymbolRepository codeSymbolRepository;

	public CodeAnalysisService(ProjectService projectService, CodeScanner codeScanner, JavaCodeIndexer javaCodeIndexer,
			CodeFileRepository codeFileRepository, CodeSymbolRepository codeSymbolRepository) {
		this.projectService = projectService;
		this.codeScanner = codeScanner;
		this.javaCodeIndexer = javaCodeIndexer;
		this.codeFileRepository = codeFileRepository;
		this.codeSymbolRepository = codeSymbolRepository;
	}

	@Transactional
	public CodeScanResult scan(Long projectId) {
		Project project = projectService.getProject(projectId);
		Path rootPath = Path.of(project.getRootPath());

		codeSymbolRepository.deleteByProjectId(projectId);
		codeFileRepository.deleteByProjectId(projectId);

		List<Path> javaFiles = codeScanner.scanJavaFiles(rootPath);
		for (Path javaFile : javaFiles) {
			CodeFile codeFile = saveCodeFile(project, rootPath, javaFile);
			codeSymbolRepository.saveAll(javaCodeIndexer.index(project, codeFile, javaFile));
		}

		return new CodeScanResult(
				projectId,
				codeFileRepository.countByProjectId(projectId),
				codeSymbolRepository.countByProjectId(projectId)
		);
	}

	@Transactional(readOnly = true)
	public List<CodeFileResponse> findFiles(Long projectId) {
		projectService.getProject(projectId);
		return codeFileRepository.findByProjectIdOrderByPathAsc(projectId).stream()
				.map(CodeFileResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<CodeSymbolResponse> findSymbols(Long projectId) {
		projectService.getProject(projectId);
		return codeSymbolRepository.findByProjectIdOrderByFilePathAscStartLineAsc(projectId).stream()
				.map(CodeSymbolResponse::from)
				.toList();
	}

	private CodeFile saveCodeFile(Project project, Path rootPath, Path javaFile) {
		try {
			String relativePath = rootPath.relativize(javaFile).toString();
			CodeFile codeFile = new CodeFile(
					project,
					relativePath,
					javaFile.getFileName().toString(),
					"JAVA",
					isTestFile(relativePath),
					Files.size(javaFile),
					Files.getLastModifiedTime(javaFile).toInstant()
			);
			return codeFileRepository.save(codeFile);
		} catch (IOException exception) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Code file metadata cannot be read");
		}
	}

	private boolean isTestFile(String relativePath) {
		String normalized = relativePath.replace('\\', '/');
		return normalized.contains("/src/test/")
				|| normalized.startsWith("src/test/")
				|| normalized.endsWith("Test.java")
				|| normalized.endsWith("Tests.java");
	}
}
