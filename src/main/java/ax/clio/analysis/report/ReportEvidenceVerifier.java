package ax.clio.analysis.report;

import ax.clio.analysis.pipeline.contract.CodeFlow;
import ax.clio.analysis.pipeline.contract.FlowNode;
import ax.clio.analysis.pipeline.contract.GeneratedReport;
import ax.clio.analysis.pipeline.contract.RelatedCodeEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * LLM이 생성한 리포트(#10)가 검색된 근거에 없는 파일·클래스를 언급하는지 사후 검증한다 (roadmap #11 / 4.4).
 *
 * <p><b>참조 검증이지 의미 검증이 아니다</b>: "언급한 파일/클래스가 근거에 존재하나"만 본다. 최대 리스크는
 * 오탐이라, 추출은 보수적으로(확실한 코드 식별자만) 매칭은 관대하게(대소문자·확장자 무시) 한다(E2).
 * 허용 근거는 relatedCode + flows(E1). 위반은 경고 목록으로 반환하고 호출자가 노출한다(E3=경고만, E4).
 */
@Component
public class ReportEvidenceVerifier {

	/** `*.java` 파일명. 예: PaymentService.java */
	private static final Pattern JAVA_FILE = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\.java\\b");
	/** 대문자 언덕 2개 이상인 CamelCase 클래스명. 예: PaymentService (단, Payment·JSON은 제외 → 오탐 방지) */
	private static final Pattern CAMEL_CASE = Pattern.compile("\\b[A-Z][a-z0-9]+(?:[A-Z][a-z0-9]*)+\\b");

	/**
	 * LLM 리포트에서 근거 밖 코드 언급을 찾아 경고 문자열 목록으로 반환한다(없으면 빈 리스트).
	 * 검증 자체는 분석을 깨지 않는다 — 예상 밖 입력엔 빈 리스트로 안전하게 통과시킨다.
	 */
	public List<String> findUnsupportedReferences(GeneratedReport report, List<RelatedCodeEntry> relatedCode,
			List<CodeFlow> flows) {
		if (report == null) {
			return List.of();
		}
		Set<String> allowed = allowedKeys(relatedCode, flows);
		String text = String.join("\n", nullToEmpty(report.summary()),
				nullToEmpty(report.recommendedFix()), nullToEmpty(report.recommendedTests()));

		// key(정규화된 stem) → 원본 표기(첫 등장). 중복 언급은 하나로 합친다.
		Map<String, String> unsupported = new LinkedHashMap<>();
		collectMentions(JAVA_FILE, text, allowed, unsupported);
		collectMentions(CAMEL_CASE, text, allowed, unsupported);

		return unsupported.values().stream()
				.map(original -> "근거 없는 코드 언급: " + original)
				.toList();
	}

	private void collectMentions(Pattern pattern, String text, Set<String> allowed, Map<String, String> unsupported) {
		Matcher matcher = pattern.matcher(text);
		while (matcher.find()) {
			String original = matcher.group();
			String key = normalize(original);
			if (key.isEmpty() || allowed.contains(key)) {
				continue;
			}
			// 표시는 클래스명 형태로 통일(파일명·클래스 언급을 한 참조로 합침).
			unsupported.putIfAbsent(key, stripJavaSuffix(original));
		}
	}

	private Set<String> allowedKeys(List<RelatedCodeEntry> relatedCode, List<CodeFlow> flows) {
		Set<String> allowed = new java.util.HashSet<>();
		if (relatedCode != null) {
			for (RelatedCodeEntry entry : relatedCode) {
				addAllowed(allowed, entry.filePath());
				addAllowed(allowed, entry.symbolName());
			}
		}
		if (flows != null) {
			for (CodeFlow flow : flows) {
				if (flow.nodes() == null) {
					continue;
				}
				for (FlowNode node : flow.nodes()) {
					addAllowed(allowed, node.filePath());
					addAllowed(allowed, node.className());
				}
			}
		}
		return allowed;
	}

	private void addAllowed(Set<String> allowed, String value) {
		if (value == null || value.isBlank()) {
			return;
		}
		String basename = value.substring(Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\')) + 1);
		String key = normalize(basename);
		if (!key.isEmpty()) {
			allowed.add(key);
		}
	}

	/** 매칭 관대화: 소문자 + 경로 제거 + `.java` 확장자 제거 → 파일명과 클래스명을 같은 키로 본다. */
	private String normalize(String value) {
		String lower = value.toLowerCase();
		int slash = Math.max(lower.lastIndexOf('/'), lower.lastIndexOf('\\'));
		if (slash >= 0) {
			lower = lower.substring(slash + 1);
		}
		if (lower.endsWith(".java")) {
			lower = lower.substring(0, lower.length() - ".java".length());
		}
		return lower;
	}

	private String stripJavaSuffix(String value) {
		return value.regionMatches(true, value.length() - ".java".length(), ".java", 0, ".java".length())
				? value.substring(0, value.length() - ".java".length())
				: value;
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
