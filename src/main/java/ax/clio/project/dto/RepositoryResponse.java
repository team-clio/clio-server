package ax.clio.project.dto;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import ax.clio.project.entity.ProjectSource;

public record RepositoryResponse(
		Long id,
		Long projectId,
		String provider,
		String owner,
		String name,
		String url,
		String defaultBranch,
		List<String> includePaths,
		List<String> excludePaths,
		boolean enabled,
		String syncStatus,
		Instant lastSyncedAt,
		Instant createdAt,
		Instant updatedAt
) {
	public static RepositoryResponse from(ProjectSource source) {
		return new RepositoryResponse(
				source.getId(), source.getProject().getId(), source.getProvider().name(),
				source.getOwner(), source.getName(), source.getRepoUrl(), source.getTargetBranch(),
				toList(source.getIncludePaths()), toList(source.getExcludePaths()), source.isEnabled(),
				source.getSyncStatus().name(), source.getLastSyncedAt(), source.getCreatedAt(), source.getUpdatedAt()
		);
	}

	private static List<String> toList(String paths) {
		return paths == null || paths.isBlank() ? List.of() : Arrays.asList(paths.split("\\n"));
	}
}
