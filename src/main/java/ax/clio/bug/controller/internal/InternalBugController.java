package ax.clio.bug.controller.internal;

import static ax.clio.common.api.ApiPaths.INTERNAL_V1;

import ax.clio.bug.dto.AgentBugResponse;
import ax.clio.bug.service.BugService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping(INTERNAL_V1 + "/projects/{projectId}/bugs")
@RequiredArgsConstructor
@Validated
public class InternalBugController {
	private final BugService bugService;

	@GetMapping("/{bugId}")
	public AgentBugResponse get(@PathVariable Long projectId, @PathVariable Long bugId) {
		return bugService.getForAgent(projectId, bugId);
	}

	@GetMapping
	public List<AgentBugResponse> list(
			@PathVariable Long projectId,
			@RequestParam(name = "after_bug_id", defaultValue = "0") @Min(0) Long afterBugId,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
	) {
		return bugService.listForAgent(projectId, afterBugId, limit);
	}
}
