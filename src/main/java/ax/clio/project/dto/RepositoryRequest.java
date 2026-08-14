package ax.clio.project.dto;

import java.util.List;

import ax.clio.project.entity.RepositoryProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RepositoryRequest(
		@NotNull RepositoryProvider provider,
		@NotBlank @Size(max = 255) String owner,
		@NotBlank @Size(max = 255) String name,
		@NotBlank @Size(max = 1000) String url,
		@NotBlank @Size(max = 255) String defaultBranch,
		@NotNull List<@Size(max = 1000) String> includePaths,
		@NotNull List<@Size(max = 1000) String> excludePaths,
		boolean enabled
) {
}
