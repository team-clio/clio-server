package ax.clio.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectCreateRequest(
		@NotBlank
		@Size(max = 120)
		String name,

		@NotBlank
		@Size(max = 1000)
		String rootPath,

		@Size(max = 1000)
		String description
) {
}
