package ax.clio.internal.agent;

import ax.clio.bug.dto.AgentBugReportResponse;
import ax.clio.bug.service.BugReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/api/v1/projects/{projectId}/bug-reports")
@RequiredArgsConstructor
public class AgentBugReportController {

	private final BugReportService bugReportService;

	@GetMapping("/{reportId}")
	public AgentBugReportResponse getBugReport(
			@PathVariable Long projectId,
			@PathVariable Long reportId
	) {
		return bugReportService.getForAgent(projectId, reportId);
	}
}
