package ax.clio.workflow.entity;

import java.time.Instant;
import java.util.Objects;

import ax.clio.project.entity.Project;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
		name = "agent_workflow_runs",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_workflow_runs_project_request",
				columnNames = {"project_id", "request_id"}
		)
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentWorkflowRun {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Column(name = "request_id", nullable = false, length = 255)
	private String requestId;

	@Column(nullable = false, length = 80)
	private String requestType;

	@Column(nullable = false, length = 64)
	private String requestHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private AgentWorkflowStatus status;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private JsonNode requestPayload;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private JsonNode latestCheckpoint;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private JsonNode resultSnapshot;

	@Column(length = 100)
	private String failureCode;

	@Column(length = 2000)
	private String failureMessage;

	private Instant startedAt;

	private Instant completedAt;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	public static AgentWorkflowRun pending(
			Project project,
			String requestId,
			String requestType,
			String requestHash,
			JsonNode requestPayload
	) {
		AgentWorkflowRun run = new AgentWorkflowRun();
		run.project = Objects.requireNonNull(project);
		run.requestId = requireText(requestId, "requestId");
		run.requestType = requireText(requestType, "requestType");
		run.requestHash = Objects.requireNonNull(requestHash);
		run.requestPayload = Objects.requireNonNull(requestPayload).deepCopy();
		run.status = AgentWorkflowStatus.PENDING;
		return run;
	}

	public void start() {
		if (status != AgentWorkflowStatus.PENDING) {
			throw new IllegalStateException("Only a PENDING workflow can start.");
		}
		status = AgentWorkflowStatus.RUNNING;
		startedAt = Instant.now();
	}

	public void checkpoint(JsonNode checkpoint) {
		if (status != AgentWorkflowStatus.RUNNING) {
			throw new IllegalStateException("Only a RUNNING workflow can save a checkpoint.");
		}
		latestCheckpoint = Objects.requireNonNull(checkpoint).deepCopy();
	}

	public void complete(JsonNode result) {
		if (status != AgentWorkflowStatus.RUNNING) {
			throw new IllegalStateException("Only a RUNNING workflow can complete.");
		}
		resultSnapshot = result == null ? null : result.deepCopy();
		status = AgentWorkflowStatus.COMPLETED;
		completedAt = Instant.now();
	}

	public void fail(String code, String message, JsonNode checkpoint) {
		if (status != AgentWorkflowStatus.PENDING && status != AgentWorkflowStatus.RUNNING) {
			throw new IllegalStateException("Only a PENDING or RUNNING workflow can fail.");
		}
		failureCode = requireText(code, "failureCode");
		failureMessage = requireText(message, "failureMessage");
		latestCheckpoint = checkpoint == null ? latestCheckpoint : checkpoint.deepCopy();
		status = AgentWorkflowStatus.FAILED;
		completedAt = Instant.now();
	}

	public JsonNode getRequestPayload() {
		return requestPayload.deepCopy();
	}

	public JsonNode getLatestCheckpoint() {
		return latestCheckpoint == null ? null : latestCheckpoint.deepCopy();
	}

	public JsonNode getResultSnapshot() {
		return resultSnapshot == null ? null : resultSnapshot.deepCopy();
	}

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void preUpdate() {
		updatedAt = Instant.now();
	}

	private static String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " is required.");
		}
		return value.trim();
	}
}
