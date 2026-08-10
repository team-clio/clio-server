package ax.clio.common.idempotency;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
		name = "agent_operations",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_agent_operations_project_type_request",
				columnNames = {"project_id", "operation_type", "request_id"}
		)
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentOperation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Enumerated(EnumType.STRING)
	@Column(name = "operation_type", nullable = false, length = 50)
	private AgentOperationType operationType;

	@Column(name = "request_id", nullable = false, length = 255)
	private String requestId;

	@Column(nullable = false, length = 64)
	private String requestHash;

	@Column(nullable = false)
	private int responseStatus;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private JsonNode responseBody;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	public static AgentOperation completed(
			Project project,
			AgentOperationType operationType,
			String requestId,
			String requestHash,
			int responseStatus,
			JsonNode responseBody
	) {
		AgentOperation operation = new AgentOperation();
		operation.project = Objects.requireNonNull(project);
		operation.operationType = Objects.requireNonNull(operationType);
		operation.requestId = requireRequestId(requestId);
		operation.requestHash = Objects.requireNonNull(requestHash);
		operation.responseStatus = responseStatus;
		operation.responseBody = Objects.requireNonNull(responseBody).deepCopy();
		return operation;
	}

	public JsonNode getResponseBody() {
		return responseBody.deepCopy();
	}

	@PrePersist
	void prePersist() {
		this.createdAt = Instant.now();
	}

	private static String requireRequestId(String requestId) {
		if (requestId == null || requestId.isBlank() || requestId.length() > 255) {
			throw new IllegalArgumentException("requestId must contain between 1 and 255 characters.");
		}
		return requestId;
	}
}
