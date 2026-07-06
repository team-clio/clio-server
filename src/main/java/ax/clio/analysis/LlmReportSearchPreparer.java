package ax.clio.analysis;

import java.util.ArrayList;
import java.util.List;

import ax.clio.common.BusinessException;
import ax.clio.llm.LlmClient;
import ax.clio.llm.LlmConfig;
import ax.clio.report.BugReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class LlmReportSearchPreparer implements ReportSearchPreparer {

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
			return new ReportSearchPreparation(
					text(content, "reportType", "UNKNOWN"),
					textList(content, "businessTerms"),
					filterCandidates(textList(content, "candidateDomains"), domainCandidates),
					textList(content, "symptoms"),
					textList(content, "codeSearchTerms"),
					text(content, "confidence", "LOW")
			);
		} catch (BusinessException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new BusinessException(HttpStatus.BAD_GATEWAY, "Failed to parse LLM response");
		}
	}

	private List<String> filterCandidates(List<String> values, List<String> domainCandidates) {
		return values.stream()
				.filter(domainCandidates::contains)
				.distinct()
				.toList();
	}

	private List<String> textList(JsonNode node, String fieldName) {
		JsonNode field = node.path(fieldName);
		List<String> values = new ArrayList<>();
		if (!field.isArray()) {
			return values;
		}
		for (JsonNode item : field) {
			String value = item.asText("").strip();
			if (!value.isBlank()) {
				values.add(value);
			}
		}
		return values;
	}

	private String text(JsonNode node, String fieldName, String defaultValue) {
		String value = node.path(fieldName).asText(defaultValue).strip();
		return value.isBlank() ? defaultValue : value;
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
