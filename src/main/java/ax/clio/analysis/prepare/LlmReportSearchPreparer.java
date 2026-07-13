package ax.clio.analysis.prepare;

import ax.clio.analysis.pipeline.ReportSearchPreparation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import ax.clio.common.BusinessException;
import ax.clio.llm.client.LlmClient;
import ax.clio.llm.entity.LlmConfig;
import ax.clio.report.entity.BugReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class LlmReportSearchPreparer implements ReportSearchPreparer {

	private static final Set<String> REPORT_TYPES = Set.of("USER_REPORT", "OPERATION_REPORT", "DEVELOPER_REPORT", "UNKNOWN");
	private static final Set<String> SYMPTOMS = Set.of("NOT_VISIBLE", "NOT_FOUND", "STATE_NOT_UPDATED", "WRONG_VALUE",
			"DUPLICATED", "DELAYED", "FAILED", "UNAUTHORIZED", "TIMEOUT", "ERROR", "INCONSISTENT", "UNKNOWN");
	private static final Set<String> CONFIDENCES = Set.of("LOW", "MEDIUM", "HIGH");
	private static final int MAX_LIST_SIZE = 20;
	private static final int MAX_TEXT_LENGTH = 120;

	private final CodebaseDomainCandidateProvider domainCandidateProvider;
	private final LlmClient llmClient;
	private final ObjectMapper objectMapper;

	public LlmReportSearchPreparer(CodebaseDomainCandidateProvider domainCandidateProvider, LlmClient llmClient) {
		this.domainCandidateProvider = domainCandidateProvider;
		this.llmClient = llmClient;
		this.objectMapper = new ObjectMapper();
	}

	@Override
	public ReportSearchPreparation prepare(BugReport report, LlmConfig llmConfig, String model) {
		List<String> domainCandidates = domainCandidateProvider.findCandidates(report.getProject().getId());
		String response = llmClient.completeJson(
				llmConfig,
				model,
				systemPrompt(),
				userPrompt(report, domainCandidates)
		);
		return parse(response, domainCandidates);
	}

	private ReportSearchPreparation parse(String response, List<String> domainCandidates) {
		try {
			JsonNode root = objectMapper.readTree(response);
			JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
			if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
				throw new BusinessException(HttpStatus.BAD_GATEWAY, "LLM response content is empty");
			}
			JsonNode content = objectMapper.readTree(contentNode.asText());
			validateObject(content);
			return new ReportSearchPreparation(
					enumText(content, "reportType", REPORT_TYPES),
					textList(content, "businessTerms"),
					filterCandidates(textList(content, "candidateDomains"), domainCandidates),
					enumList(content, "symptoms", SYMPTOMS),
					textList(content, "codeSearchTerms"),
					enumText(content, "confidence", CONFIDENCES)
			);
		} catch (BusinessException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new BusinessException(HttpStatus.BAD_GATEWAY, "Failed to parse LLM response");
		}
	}

	private void validateObject(JsonNode content) {
		if (!content.isObject()) {
			throw invalid("content must be a JSON object");
		}
		require(content, "reportType");
		require(content, "businessTerms");
		require(content, "candidateDomains");
		require(content, "symptoms");
		require(content, "codeSearchTerms");
		require(content, "confidence");
	}

	private void require(JsonNode node, String fieldName) {
		if (!node.has(fieldName)) {
			throw invalid("missing field '" + fieldName + "'");
		}
	}

	private List<String> filterCandidates(List<String> values, List<String> domainCandidates) {
		return values.stream()
				.filter(domainCandidates::contains)
				.distinct()
				.toList();
	}

	private List<String> textList(JsonNode node, String fieldName) {
		JsonNode field = node.get(fieldName);
		if (!field.isArray()) {
			throw invalid("field '" + fieldName + "' must be an array");
		}
		Set<String> values = new LinkedHashSet<>();
		for (JsonNode item : field) {
			if (!item.isTextual()) {
				throw invalid("field '" + fieldName + "' must contain only strings");
			}
			String value = item.asText("").strip();
			if (!value.isBlank() && value.length() <= MAX_TEXT_LENGTH) {
				values.add(value);
			}
		}
		return values.stream()
				.limit(MAX_LIST_SIZE)
				.toList();
	}

	private List<String> enumList(JsonNode node, String fieldName, Set<String> allowedValues) {
		JsonNode field = node.get(fieldName);
		if (!field.isArray()) {
			throw invalid("field '" + fieldName + "' must be an array");
		}
		Set<String> values = new LinkedHashSet<>();
		for (JsonNode item : field) {
			if (!item.isTextual()) {
				throw invalid("field '" + fieldName + "' must contain only strings");
			}
			String value = item.asText("").strip();
			if (value.isBlank()) {
				continue;
			}
			if (!allowedValues.contains(value)) {
				throw invalid("field '" + fieldName + "' has unsupported value '" + value + "'");
			}
			values.add(value);
		}
		return values.stream()
				.limit(MAX_LIST_SIZE)
				.toList();
	}

	private String enumText(JsonNode node, String fieldName, Set<String> allowedValues) {
		JsonNode field = node.get(fieldName);
		if (!field.isTextual()) {
			throw invalid("field '" + fieldName + "' must be a string");
		}
		String value = field.asText("").strip();
		if (value.isBlank()) {
			throw invalid("field '" + fieldName + "' is empty");
		}
		if (!allowedValues.contains(value)) {
			throw invalid("field '" + fieldName + "' has unsupported value '" + value + "'");
		}
		return value;
	}

	private BusinessException invalid(String reason) {
		return new BusinessException(HttpStatus.BAD_GATEWAY, "Invalid LLM search input response: " + reason);
	}

	private String systemPrompt() {
		return """
				You convert bug reports into code search inputs.
				Return JSON only.
				Do not infer root causes, exact classes, methods, files, fixes, or tests.
				candidateDomains must be selected only from the provided codebase domain candidates.
				Use English enum values and code search terms.
				Schema:
				{
				  "reportType": "USER_REPORT | OPERATION_REPORT | DEVELOPER_REPORT | UNKNOWN",
				  "businessTerms": ["string"],
				  "candidateDomains": ["string"],
				  "symptoms": ["NOT_VISIBLE | NOT_FOUND | STATE_NOT_UPDATED | WRONG_VALUE | DUPLICATED | DELAYED | FAILED | UNAUTHORIZED | TIMEOUT | ERROR | INCONSISTENT | UNKNOWN"],
				  "codeSearchTerms": ["string"],
				  "confidence": "LOW | MEDIUM | HIGH"
				}
				""";
	}

	private String userPrompt(BugReport report, List<String> domainCandidates) {
		return """
				Report title:
				%s

				Report description:
				%s

				Codebase domain candidates:
				%s
				""".formatted(report.getTitle(), report.getDescription(), domainCandidates);
	}
}
