package ax.clio.workflow.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import ax.clio.common.ConflictException;
import ax.clio.common.ResourceNotFoundException;
import ax.clio.project.entity.Project;
import ax.clio.project.repository.ProjectRepository;
import ax.clio.workflow.dto.CreateWorkflowRunRequest;
import ax.clio.workflow.dto.UpdateWorkflowRunRequest;
import ax.clio.workflow.dto.WorkflowRunResponse;
import ax.clio.workflow.entity.AgentWorkflowRun;
import ax.clio.workflow.entity.AgentWorkflowStatus;
import ax.clio.workflow.repository.AgentWorkflowRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class AgentWorkflowRunService {
	private static final com.fasterxml.jackson.databind.ObjectMapper PERSISTENCE_MAPPER =
			new com.fasterxml.jackson.databind.ObjectMapper();

	private final ProjectRepository projectRepository;
	private final AgentWorkflowRunRepository workflowRunRepository;
	private final ObjectMapper objectMapper;

	@Transactional
	public WorkflowRunResponse createPending(Long projectId, CreateWorkflowRunRequest request) {
		Project project = projectRepository.findByIdForUpdate(projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
		String hash = requestHash(request.requestType(), request.requestPayload());
		AgentWorkflowRun run = workflowRunRepository.findByProjectIdAndRequestId(projectId, request.requestId())
				.map(existing -> verifyReplay(existing, hash))
				.orElseGet(() -> workflowRunRepository.save(AgentWorkflowRun.pending(
						project,
						request.requestId(),
						request.requestType(),
						hash,
						persistenceTree(request.requestPayload())
				)));
		return response(run);
	}

	@Transactional
	public WorkflowRunResponse update(Long projectId, Long runId, UpdateWorkflowRunRequest request) {
		AgentWorkflowRun run = findForUpdate(projectId, runId);
		com.fasterxml.jackson.databind.JsonNode checkpoint = persistenceTreeOrNull(request.latestCheckpoint());
		switch (request.status()) {
			case PENDING -> throw new IllegalArgumentException("A workflow cannot transition back to PENDING.");
			case RUNNING -> updateRunning(run, checkpoint);
			case COMPLETED -> updateCompleted(run, persistenceTreeOrNull(request.resultSnapshot()));
			case FAILED -> updateFailed(run, request.failureCode(), request.failureMessage(), checkpoint);
		}
		return response(run);
	}

	@Transactional(readOnly = true)
	public WorkflowRunResponse get(Long projectId, Long runId) {
		return response(workflowRunRepository.findByIdAndProjectId(runId, projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Workflow run not found: " + runId)));
	}

	@Transactional(readOnly = true)
	public AgentWorkflowRun requireRunning(Long projectId, Long runId) {
		AgentWorkflowRun run = workflowRunRepository.findByIdAndProjectId(runId, projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Workflow run not found: " + runId));
		if (run.getStatus() != AgentWorkflowStatus.RUNNING) {
			throw new ConflictException("Workflow run must be RUNNING: " + runId);
		}
		return run;
	}

	private AgentWorkflowRun findForUpdate(Long projectId, Long runId) {
		return workflowRunRepository.findByIdAndProjectId(runId, projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Workflow run not found: " + runId));
	}

	private AgentWorkflowRun verifyReplay(AgentWorkflowRun existing, String hash) {
		if (!existing.getRequestHash().equals(hash)) {
			throw new ConflictException(
					"requestId was already used with a different workflow request: " + existing.getRequestId()
			);
		}
		return existing;
	}

	private void updateRunning(AgentWorkflowRun run, com.fasterxml.jackson.databind.JsonNode checkpoint) {
		if (run.getStatus() == AgentWorkflowStatus.PENDING) {
			run.start();
		} else if (run.getStatus() != AgentWorkflowStatus.RUNNING || checkpoint == null) {
			throw new ConflictException("Workflow has already been claimed: " + run.getId());
		}
		if (checkpoint != null) {
			run.checkpoint(checkpoint);
		}
	}

	private void updateCompleted(AgentWorkflowRun run, com.fasterxml.jackson.databind.JsonNode result) {
		if (run.getStatus() == AgentWorkflowStatus.COMPLETED) {
			if (!java.util.Objects.equals(run.getResultSnapshot(), result)) {
				throw new ConflictException("Workflow result differs from the stored completed result: " + run.getId());
			}
			return;
		}
		run.complete(result);
	}

	private void updateFailed(
			AgentWorkflowRun run,
			String code,
			String message,
			com.fasterxml.jackson.databind.JsonNode checkpoint
	) {
		if (run.getStatus() == AgentWorkflowStatus.FAILED) {
			if (!java.util.Objects.equals(run.getFailureCode(), code)
					|| !java.util.Objects.equals(run.getFailureMessage(), message)) {
				throw new ConflictException("Workflow failure differs from the stored failure: " + run.getId());
			}
			return;
		}
		run.fail(code, message, checkpoint);
	}

	private WorkflowRunResponse response(AgentWorkflowRun run) {
		return new WorkflowRunResponse(
				run.getId(),
				run.getProject().getId(),
				run.getRequestId(),
				run.getRequestType(),
				run.getStatus(),
				apiTree(run.getLatestCheckpoint()),
				apiTree(run.getResultSnapshot()),
				run.getFailureCode(),
				run.getFailureMessage(),
				run.getStartedAt(),
				run.getCompletedAt(),
				run.getCreatedAt(),
				run.getUpdatedAt()
		);
	}

	private String requestHash(String requestType, JsonNode payload) {
		ObjectNode request = objectMapper.createObjectNode();
		request.put("request_type", requestType.trim());
		request.set("request_payload", canonicalize(payload));
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256")
					.digest(request.toString().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available.", exception);
		}
	}

	private JsonNode canonicalize(JsonNode node) {
		if (node.isObject()) {
			ObjectNode canonical = objectMapper.createObjectNode();
			node.propertyStream().sorted(java.util.Map.Entry.comparingByKey())
					.forEach(entry -> canonical.set(entry.getKey(), canonicalize(entry.getValue())));
			return canonical;
		}
		if (node.isArray()) {
			ArrayNode canonical = objectMapper.createArrayNode();
			node.forEach(item -> canonical.add(canonicalize(item)));
			return canonical;
		}
		return node.deepCopy();
	}

	private com.fasterxml.jackson.databind.JsonNode persistenceTree(JsonNode node) {
		try {
			return PERSISTENCE_MAPPER.readTree(node.toString());
		} catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
			throw new IllegalStateException("Workflow JSON cannot be persisted.", exception);
		}
	}

	private com.fasterxml.jackson.databind.JsonNode persistenceTreeOrNull(JsonNode node) {
		return node == null ? null : persistenceTree(node);
	}

	private JsonNode apiTree(com.fasterxml.jackson.databind.JsonNode node) {
		return node == null ? null : objectMapper.readTree(node.toString());
	}
}
