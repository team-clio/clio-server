package ax.clio.code.service;

import ax.clio.code.entity.CodeFile;
import ax.clio.code.entity.CodeSearchMatchType;
import ax.clio.code.entity.CodeSearchResult;
import ax.clio.code.entity.CodeSymbol;
import ax.clio.code.entity.CodeSymbolType;
import ax.clio.code.repository.CodeFileRepository;
import ax.clio.code.repository.CodeSymbolRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import ax.clio.project.entity.Project;
import ax.clio.project.service.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CodeSearchService {

	private static final int DEFAULT_LIMIT = 50;

	private final ProjectService projectService;
	private final CodeFileRepository codeFileRepository;
	private final CodeSymbolRepository codeSymbolRepository;

	public CodeSearchService(ProjectService projectService, CodeFileRepository codeFileRepository,
			CodeSymbolRepository codeSymbolRepository) {
		this.projectService = projectService;
		this.codeFileRepository = codeFileRepository;
		this.codeSymbolRepository = codeSymbolRepository;
	}

	@Transactional(readOnly = true)
	public List<CodeSearchResult> search(Long projectId, String query, Integer limit) {
		Project project = projectService.getProject(projectId);
		String normalizedQuery = normalize(query);
		if (normalizedQuery.isBlank()) {
			return List.of();
		}

		List<CodeSearchResult> results = new ArrayList<>();
		results.addAll(searchSymbols(projectId, normalizedQuery));
		results.addAll(searchFileBodies(project, normalizedQuery));

		int resultLimit = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 200);
		return results.stream()
				.sorted(Comparator.comparingInt(CodeSearchResult::score).reversed()
						.thenComparing(CodeSearchResult::filePath)
						.thenComparing(result -> result.lineNumber() == null ? Integer.MAX_VALUE : result.lineNumber()))
				.limit(resultLimit)
				.toList();
	}

	private List<CodeSearchResult> searchSymbols(Long projectId, String query) {
		return codeSymbolRepository.findByProjectId(projectId).stream()
				.filter(symbol -> matches(symbol, query))
				.map(symbol -> new CodeSearchResult(
						symbol.getFile().getId(),
						symbol.getFile().getPath(),
						symbol.getName(),
						symbol.getType(),
						symbol.getRole(),
						matchType(symbol, query),
						symbol.getStartLine(),
						symbol.getName(),
						score(symbol, query),
						symbol.getFile().isTest()
				))
				.toList();
	}

	private boolean matches(CodeSymbol symbol, String query) {
		return normalize(symbol.getName()).contains(query)
				|| normalize(symbol.getAnnotations()).contains(query)
				|| normalize(symbol.getFile().getFileName()).contains(query);
	}

	private CodeSearchMatchType matchType(CodeSymbol symbol, String query) {
		if (normalize(symbol.getName()).contains(query)) {
			if (symbol.getType() == CodeSymbolType.METHOD || symbol.getType() == CodeSymbolType.CONSTRUCTOR) {
				return CodeSearchMatchType.METHOD_NAME;
			}
			return CodeSearchMatchType.CLASS_NAME;
		}
		if (normalize(symbol.getAnnotations()).contains(query)) {
			return CodeSearchMatchType.ANNOTATION;
		}
		return CodeSearchMatchType.FILE_NAME;
	}

	private int score(CodeSymbol symbol, String query) {
		int baseScore = switch (matchType(symbol, query)) {
			case CLASS_NAME -> 100;
			case METHOD_NAME -> 90;
			case ANNOTATION -> 80;
			case FILE_NAME -> 70;
			case CODE_TEXT -> 50;
		};
		return symbol.getFile().isTest() ? baseScore - 10 : baseScore;
	}

	private List<CodeSearchResult> searchFileBodies(Project project, String query) {
		Path rootPath = Path.of(project.getRootPath()).toAbsolutePath().normalize();
		List<CodeSearchResult> results = new ArrayList<>();
		for (CodeFile codeFile : codeFileRepository.findByProjectIdOrderByPathAsc(project.getId())) {
			Path filePath = rootPath.resolve(codeFile.getPath()).normalize();
			if (!filePath.startsWith(rootPath) || !Files.isRegularFile(filePath)) {
				continue;
			}
			try {
				List<String> lines = Files.readAllLines(filePath);
				for (int index = 0; index < lines.size(); index++) {
					String line = lines.get(index);
					if (normalize(line).contains(query)) {
						int score = codeFile.isTest() ? 40 : 50;
						results.add(new CodeSearchResult(
								codeFile.getId(),
								codeFile.getPath(),
								null,
								null,
								null,
								CodeSearchMatchType.CODE_TEXT,
								index + 1,
								line.strip(),
								score,
								codeFile.isTest()
						));
					}
				}
			} catch (IOException ignored) {
				// A stale file entry should not fail the whole search request.
			}
		}
		return results;
	}

	private String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}
}
