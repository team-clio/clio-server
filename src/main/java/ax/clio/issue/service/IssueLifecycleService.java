package ax.clio.issue.service;

import java.math.BigDecimal;

import ax.clio.bug.entity.Bug;
import ax.clio.bug.repository.BugRepository;
import ax.clio.common.ConflictException;
import ax.clio.common.ResourceNotFoundException;
import ax.clio.issue.dto.CreateIssueFromBugRequest;
import ax.clio.issue.dto.IssueBugLifecycleResponse;
import ax.clio.issue.dto.IssueSummaryResponse;
import ax.clio.issue.dto.LinkBugToIssueRequest;
import ax.clio.issue.dto.UpdateIssueRequest;
import ax.clio.issue.entity.Issue;
import ax.clio.issue.entity.IssueBug;
import ax.clio.issue.repository.IssueBugRepository;
import ax.clio.issue.repository.IssueRepository;
import ax.clio.workflow.entity.AgentWorkflowRun;
import ax.clio.workflow.service.AgentWorkflowRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IssueLifecycleService {

	private final IssueRepository issueRepository;
	private final IssueBugRepository issueBugRepository;
	private final BugRepository bugRepository;
	private final AgentWorkflowRunService workflowRunService;

	@Transactional
	public IssueBugLifecycleResponse createFromBug(Long projectId, CreateIssueFromBugRequest request) {
		requireBugWorkflow(projectId, request.workflowRunId(), request.bugId());
		Bug bug = requireBug(projectId, request.bugId());
		IssueBug existing = issueBugRepository.findByBugId(bug.getId()).orElse(null);
		if (existing != null) {
			return response(existing, false, false);
		}

		Issue issue = issueRepository.save(Issue.createFromBug(
				bug, request.confidence(), request.title(), request.description()
		));
		IssueBug link = persistLink(issue, bug, request.confidence());
		return response(link, true, true);
	}

	@Transactional
	public IssueBugLifecycleResponse linkBug(Long projectId, Long issueId, LinkBugToIssueRequest request) {
		requireBugWorkflow(projectId, request.workflowRunId(), request.bugId());
		Bug bug = requireBug(projectId, request.bugId());
		Issue issue = issueRepository.findByIdAndProjectId(issueId, projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueId));
		IssueBug existing = issueBugRepository.findByBugId(bug.getId()).orElse(null);
		if (existing != null) {
			if (!existing.getIssue().getId().equals(issueId)) {
				throw new ConflictException(
						"Bug is already linked to a different issue: " + existing.getIssue().getId()
				);
			}
			return response(existing, false, false);
		}

		IssueBug link = persistLink(issue, bug, request.confidence());
		return response(link, false, true);
	}

	@Transactional
	public IssueSummaryResponse update(Long projectId, Long issueId, UpdateIssueRequest request) {
		Issue issue = issueRepository.findByIdAndProjectId(issueId, projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueId));
		try {
			if (request.status() != null) {
				issue.updateStatus(request.status());
			}
			issue.updateTriage(
					request.priority() == null ? issue.getPriority() : request.priority(),
					request.severity() == null ? issue.getSeverity() : request.severity(),
					request.assigneeName() == null ? issue.getAssigneeName() : request.assigneeName()
			);
		} catch (IllegalStateException exception) {
			throw new ConflictException(exception.getMessage());
		}
		issueRepository.flush();
		return IssueSummaryResponse.from(issue);
	}

	private AgentWorkflowRun requireBugWorkflow(Long projectId, Long runId, Long bugId) {
		AgentWorkflowRun run = workflowRunService.requireRunning(projectId, runId);
		if (!"process_report".equals(run.getRequestType())) {
			throw new ConflictException("Workflow run is not a Bug processing workflow: " + runId);
		}
		long workflowBugId = run.getRequestPayload().path("bug_id").asLong(-1L);
		if (workflowBugId != bugId) {
			throw new ConflictException("Workflow run and requested Bug do not match: " + runId);
		}
		return run;
	}

	private Bug requireBug(Long projectId, Long bugId) {
		return bugRepository.findByIdAndProjectIdForUpdate(bugId, projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Bug not found: " + bugId));
	}

	private IssueBug persistLink(Issue issue, Bug bug, BigDecimal confidence) {
		issue.attach(bug, confidence);
		IssueBug link = issueBugRepository.save(IssueBug.create(issue, bug, confidence));
		bug.markTriaged();
		return link;
	}

	private IssueBugLifecycleResponse response(
			IssueBug link,
			boolean issueCreated,
			boolean bugLinked
	) {
		return new IssueBugLifecycleResponse(
				link.getIssue().getId(),
				link.getBug().getId(),
				issueCreated,
				bugLinked
		);
	}
}
