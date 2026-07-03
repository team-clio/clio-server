package ax.clio.code;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import ax.clio.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CodeScanner {

	private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
			"build",
			".gradle",
			".git",
			"target",
			"node_modules"
	);

	public List<Path> scanJavaFiles(Path rootPath) {
		List<Path> files = new ArrayList<>();
		try {
			Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
					if (!dir.equals(rootPath) && EXCLUDED_DIRECTORIES.contains(dir.getFileName().toString())) {
						return FileVisitResult.SKIP_SUBTREE;
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if (attrs.isRegularFile() && file.getFileName().toString().endsWith(".java")) {
						files.add(file);
					}
					return FileVisitResult.CONTINUE;
				}
			});
			return files;
		} catch (IOException exception) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Project directory cannot be scanned");
		}
	}
}
