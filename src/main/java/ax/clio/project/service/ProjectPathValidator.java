package ax.clio.project.service;

import ax.clio.project.entity.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import ax.clio.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ProjectPathValidator {

	private final Path workspaceRoot;

	public ProjectPathValidator(@Value("${clio.workspace-root:/workspace}") String workspaceRoot) {
		this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
	}

	public String validate(String rawPath) {
		Path path = Path.of(rawPath).toAbsolutePath().normalize();
		if (!path.startsWith(workspaceRoot)) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Project path must be under " + workspaceRoot);
		}
		if (!Files.exists(path)) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Project path does not exist");
		}
		if (!Files.isDirectory(path)) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Project path must be a directory");
		}
		try {
			return path.toRealPath().normalize().toString();
		} catch (IOException exception) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Project path cannot be resolved");
		}
	}
}
