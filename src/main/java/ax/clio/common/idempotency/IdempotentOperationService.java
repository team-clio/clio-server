package ax.clio.common.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;

import ax.clio.common.ResourceNotFoundException;
import ax.clio.project.entity.Project;
import ax.clio.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class IdempotentOperationService {

	private static final com.fasterxml.jackson.databind.ObjectMapper PERSISTENCE_MAPPER =
			new com.fasterxml.jackson.databind.ObjectMapper();

	private final ProjectRepository projectRepository;
	private final AgentOperationRepository operationRepository;
	private final ObjectMapper objectMapper;

	@Transactional
	public <T> IdempotentResponse<T> execute(
			Long projectId,
			AgentOperationType operationType,
			String requestId,
			Object requestBody,
			Class<T> responseType,
			int successStatus,
			Supplier<T> operation
	) {
		Project project = projectRepository.findByIdForUpdate(projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
		String requestHash = requestHash(requestBody);

		return operationRepository.findByProjectIdAndOperationTypeAndRequestId(
					projectId,
					operationType,
					requestId
			)
				.map(existing -> replay(existing, requestHash, responseType))
				.orElseGet(() -> executeFirst(
						project,
						operationType,
						requestId,
						requestHash,
						responseType,
						successStatus,
						operation
				));
	}

	private <T> IdempotentResponse<T> executeFirst(
			Project project,
			AgentOperationType operationType,
			String requestId,
			String requestHash,
			Class<T> responseType,
			int successStatus,
			Supplier<T> operation
	) {
		T response = operation.get();
		JsonNode responseBody = objectMapper.valueToTree(response);
		AgentOperation completed = AgentOperation.completed(
				project,
				operationType,
				requestId,
				requestHash,
				successStatus,
				persistenceTree(responseBody)
		);
		operationRepository.save(completed);
		return new IdempotentResponse<>(responseType.cast(response), successStatus, false);
	}

	private <T> IdempotentResponse<T> replay(
			AgentOperation existing,
			String requestHash,
			Class<T> responseType
	) {
		if (!existing.getRequestHash().equals(requestHash)) {
			throw new IdempotencyConflictException(
					"requestId was already used with a different payload: " + existing.getRequestId()
			);
		}
		try {
			JsonNode responseBody = objectMapper.readTree(existing.getResponseBody().toString());
			T response = objectMapper.treeToValue(responseBody, responseType);
			return new IdempotentResponse<>(response, existing.getResponseStatus(), true);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Stored idempotent response cannot be restored.", exception);
		}
	}

	private com.fasterxml.jackson.databind.JsonNode persistenceTree(JsonNode responseBody) {
		try {
			return PERSISTENCE_MAPPER.readTree(responseBody.toString());
		} catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
			throw new IllegalStateException("Idempotent response cannot be persisted.", exception);
		}
	}

	private String requestHash(Object requestBody) {
		JsonNode canonical = canonicalize(objectMapper.valueToTree(requestBody));
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available.", exception);
		}
	}

	private JsonNode canonicalize(JsonNode node) {
		if (node.isObject()) {
			ObjectNode canonical = objectMapper.createObjectNode();
			node.propertyStream()
					.sorted(java.util.Map.Entry.comparingByKey())
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
}
