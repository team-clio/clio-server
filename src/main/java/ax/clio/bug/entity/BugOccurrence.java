package ax.clio.bug.entity;

import java.time.Instant;
import java.util.List;
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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "bug_occurrences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BugOccurrence {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "bug_id")
	private Bug bug;

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

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	public static BugOccurrence collect(
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
		BugOccurrence occurrence = new BugOccurrence();
		occurrence.project = Objects.requireNonNull(project);
		occurrence.source = Objects.requireNonNull(source);
		occurrence.title = normalize(title);
		occurrence.description = normalize(description);
		occurrence.errorType = normalize(errorType);
		occurrence.message = normalize(message);
		ArrayNode stack = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
		if (stackTrace != null) {
			stackTrace.stream().map(BugOccurrence::normalize).filter(Objects::nonNull).forEach(stack::add);
		}
		occurrence.stackTrace = stack;
		occurrence.rawPayload = rawPayload == null ? null : rawPayload.deepCopy();
		occurrence.occurredAt = Objects.requireNonNull(occurredAt);
		return occurrence;
	}

	public void attachTo(Bug target) {
		Objects.requireNonNull(target);
		if (bug != null) {
			throw new IllegalStateException("Bug report is already grouped.");
		}
		if (project.getId() != null && target.getProject().getId() != null
				&& !project.getId().equals(target.getProject().getId())) {
			throw new IllegalArgumentException("Bug report and Bug must belong to the same project.");
		}
		this.bug = target;
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
		return "Bug report " + id;
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

	private static String normalize(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static String truncate(String value, int maxLength) {
		return value.length() <= maxLength ? value : value.substring(0, maxLength);
	}

	@PrePersist
	void prePersist() {
		this.createdAt = Instant.now();
	}
}
