package ax.clio.memory.decision;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DecisionMemoryCreateRequest(
		@NotNull
		Long projectId,

		@NotBlank
		@Size(max = 200)
		String title,

		@NotBlank
		@Size(max = 10000)
		String body
) {
}
