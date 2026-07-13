package ax.clio.analysis.report;

import ax.clio.analysis.pipeline.AnalysisResultDraft;
import ax.clio.analysis.pipeline.CodeFlow;
import ax.clio.analysis.pipeline.GeneratedReport;
import ax.clio.analysis.pipeline.RelatedCodeEntry;
import ax.clio.analysis.prepare.LlmReportSearchPreparer;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import ax.clio.llm.LlmClient;
import ax.clio.llm.LlmConfig;
import ax.clio.report.BugReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * rule-based 분석 draft를 근거로 사람이 읽는 리포트 텍스트(summary·fix·tests)를 LLM으로 생성한다
 * (roadmap #10 / 4.3). {@link LlmReportSearchPreparer}의 프롬프트·파싱·검증 패턴을 미러링하되, 실패 시엔
 * <b>예외를 던지지 않고 {@link Optional#empty()}</b>를 반환해 호출자가 rule-based로 폴백하게 한다(L3).
 *
 * <p>L2: LLM은 3개 텍스트 필드만 채운다(점수·근거는 rule-based 유지). L4: 입력은 검색된 근거(점수·도메인·
 * top-N 관련코드·flows·rationale)로 한정하고 "입력 밖을 지어내지 말라"고 지시한다(예방적 grounding; 사후
 * 검증은 #11). L6: 한국어 JSON 출력, 필드 길이 상한으로 방어.
 */
@Component
public class LlmReportWriter {

	private static final Logger log = LoggerFactory.getLogger(LlmReportWriter.class);

	private static final int MAX_RELATED_CODE = 5;
	private static final int MAX_FLOWS = 5;
	private static final int SUMMARY_MAX = 2000;
	private static final int FIX_MAX = 4000;
	private static final int TESTS_MAX = 4000;

	private final LlmClient llmClient;
	private final ObjectMapper objectMapper;

	public LlmReportWriter(LlmClient llmClient) {
		this.llmClient = llmClient;
		this.objectMapper = new ObjectMapper();
	}

	/** 성공 시 생성된 리포트, 실패·무효 응답 시 {@link Optional#empty()}(폴백 신호). */
	public Optional<GeneratedReport> write(BugReport report, AnalysisResultDraft draft, LlmConfig config, String model) {
		try {
			String response = llmClient.completeJson(config, model, systemPrompt(), userPrompt(report, draft));
			return Optional.of(parse(response));
		} catch (Exception exception) {
			// L3: LLM 실패는 분석을 깨지 않는다 — rule-based로 폴백하고 추적용 로그만 남긴다.
			log.info("LLM report generation failed for report {}, falling back to rule-based: {}",
					report.getId(), exception.getMessage());
			return Optional.empty();
		}
	}

	private GeneratedReport parse(String response) throws Exception {
		JsonNode root = objectMapper.readTree(response);
		JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
		if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
			throw new IllegalArgumentException("LLM response content is empty");
		}
		JsonNode content = objectMapper.readTree(contentNode.asText());
		if (!content.isObject()) {
			throw new IllegalArgumentException("content must be a JSON object");
		}
		String summary = requiredText(content, "summary", SUMMARY_MAX);
		String recommendedFix = requiredText(content, "recommendedFix", FIX_MAX);
		String recommendedTests = requiredText(content, "recommendedTests", TESTS_MAX);
		return new GeneratedReport(summary, recommendedFix, recommendedTests);
	}

	private String requiredText(JsonNode node, String fieldName, int maxLength) {
		JsonNode field = node.get(fieldName);
		if (field == null || !field.isTextual()) {
			throw new IllegalArgumentException("field '" + fieldName + "' must be a string");
		}
		String value = field.asText("").strip();
		if (value.isBlank()) {
			throw new IllegalArgumentException("field '" + fieldName + "' is empty");
		}
		return value.length() > maxLength ? value.substring(0, maxLength) : value;
	}

	private String systemPrompt() {
		return """
				You explain a bug-report analysis result to a developer, in Korean.
				The scores, related code, flows, and rationale are already decided by a rule-based analyzer.
				Do NOT invent or change scores. Do NOT mention any file, class, or method that is not in the input.
				Only explain and summarize what the input evidence supports.
				Return JSON only, in Korean, with exactly this schema:
				{
				  "summary": "분석 요약 (한국어)",
				  "recommendedFix": "추천 수정 방향 (한국어)",
				  "recommendedTests": "추천 테스트 전략 (한국어)"
				}
				""";
	}

	private String userPrompt(BugReport report, AnalysisResultDraft draft) {
		return """
				리포트 제목:
				%s

				리포트 내용:
				%s

				리포트 유형: %s
				중요도/난이도/위험도 점수: %d / %d / %d
				후보 도메인: %s

				관련 코드(상위 %d):
				%s

				영향 흐름:
				%s

				점수 근거(rule-based):
				%s
				""".formatted(
				report.getTitle(),
				report.getDescription(),
				emptyAsDash(draft.issueType()),
				draft.importanceScore(),
				draft.difficultyScore(),
				draft.riskScore(),
				emptyAsDash(draft.domains()),
				MAX_RELATED_CODE,
				describeRelatedCode(draft.relatedCode()),
				describeFlows(draft.flows()),
				emptyAsDash(draft.rationale()));
	}

	private String describeRelatedCode(List<RelatedCodeEntry> entries) {
		if (entries == null || entries.isEmpty()) {
			return "-";
		}
		return entries.stream()
				.limit(MAX_RELATED_CODE)
				.map(entry -> "- " + entry.filePath()
						+ " | " + emptyAsDash(entry.symbolName())
						+ " | " + emptyAsDash(entry.symbolRole())
						+ " | line " + entry.lineNumber())
				.collect(Collectors.joining("\n"));
	}

	private String describeFlows(List<CodeFlow> flows) {
		if (flows == null || flows.isEmpty()) {
			return "-";
		}
		return flows.stream()
				.limit(MAX_FLOWS)
				.map(CodeFlow::describe)
				.collect(Collectors.joining("\n"));
	}

	private String emptyAsDash(String value) {
		return value == null || value.isBlank() ? "-" : value;
	}
}
