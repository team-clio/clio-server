package ax.clio.bug.entity;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import ax.clio.project.entity.Project;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
		name = "bugs",
		indexes = @Index(name = "idx_bugs_project_occurred", columnList = "project_id, occurred_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bug {
	private static final Map<BugStatus, EnumSet<BugStatus>> STATUS_TRANSITIONS = Map.of(
			BugStatus.NEW, EnumSet.of(BugStatus.ANALYZING, BugStatus.TRIAGED, BugStatus.IGNORED),
			BugStatus.ANALYZING, EnumSet.of(BugStatus.NEW, BugStatus.TRIAGED, BugStatus.IGNORED),
			BugStatus.TRIAGED, EnumSet.of(BugStatus.ANALYZING, BugStatus.RESOLVED, BugStatus.IGNORED),
			BugStatus.RESOLVED, EnumSet.of(BugStatus.TRIAGED),
			BugStatus.IGNORED, EnumSet.of(BugStatus.NEW)
	);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private BugSource source;

	@Column(length = 200)
	private String title;

	@Lob
	private String description;

	@Column(length = 255)
	private String errorType;

	@Lob
	private String message;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private JsonNode stackTrace;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private JsonNode rawPayload;

	@Column(nullable = false)
	private Instant occurredAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private BugStatus status;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private Severity severity;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	public static Bug collect(
			Project project,
			BugSource source,
			String title,
			String description,
			String errorType,
			String message,
			List<String> stackTrace,
			JsonNode rawPayload,
			Instant occurredAt
	) {
		Bug bug = new Bug();
		bug.project = Objects.requireNonNull(project);
		bug.source = Objects.requireNonNull(source);
		bug.title = normalize(title);
		bug.description = normalize(description);
		bug.errorType = normalize(errorType);
		bug.message = normalize(message);
		ArrayNode stack = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
		if (stackTrace != null) {
			stackTrace.stream().map(Bug::normalize).filter(Objects::nonNull).forEach(stack::add);
		}
		bug.stackTrace = stack;
		bug.rawPayload = rawPayload == null ? null : rawPayload.deepCopy();
		bug.occurredAt = Objects.requireNonNull(occurredAt);
		bug.status = BugStatus.NEW;
		return bug;
	}

	public String displayTitle() {
		if (title != null) {
			return truncate(title, 200);
		}
		if (errorType != null) {
			return truncate(errorType, 200);
		}
		if (message != null) {
			return truncate(message, 200);
		}
		return "Bug " + id;
	}

	public String firstStackFrame() {
		return stackTrace != null && !stackTrace.isEmpty()
				? truncate(stackTrace.get(0).asText(), 1000)
				: null;
	}

	public List<String> stackTraceValues() {
		if (stackTrace == null) {
			return List.of();
		}
		return java.util.stream.StreamSupport.stream(stackTrace.spliterator(), false)
				.map(JsonNode::asText)
				.toList();
	}

	public JsonNode getRawPayload() {
		return rawPayload == null ? null : rawPayload.deepCopy();
	}

	public JsonNode getStackTrace() {
		return stackTrace.deepCopy();
	}

	public void markTriaged() {
		updateStatus(BugStatus.TRIAGED);
	}

	public void updateStatus(BugStatus nextStatus) {
		Objects.requireNonNull(nextStatus);
		if (status == nextStatus) {
			return;
		}
		if (!STATUS_TRANSITIONS.get(status).contains(nextStatus)) {
			throw new IllegalStateException("Unsupported bug status transition: " + status + " -> " + nextStatus);
		}
		this.status = nextStatus;
	}

	public void updateSeverity(Severity severity) {
		this.severity = severity;
	}

	private static String normalize(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static String truncate(String value, int maxLength) {
		return value.length() <= maxLength ? value : value.substring(0, maxLength);
	}

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void preUpdate() {
		this.updatedAt = Instant.now();
	}
}
