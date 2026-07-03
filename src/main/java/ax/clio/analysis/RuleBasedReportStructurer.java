package ax.clio.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import ax.clio.report.BugReport;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedReportStructurer {

	private static final Set<String> STOP_WORDS = Set.of(
			"the", "and", "for", "with", "from", "this", "that", "when", "after",
			"그리고", "에서", "으로", "하면", "되는", "하고", "있습니다", "입니다"
	);

	private static final Map<String, List<String>> DOMAIN_KEYWORDS = new LinkedHashMap<>();

	static {
		DOMAIN_KEYWORDS.put("PAYMENT", List.of("payment", "pay", "billing", "결제", "승인", "환불"));
		DOMAIN_KEYWORDS.put("ORDER", List.of("order", "주문", "배송", "상태", "status"));
		DOMAIN_KEYWORDS.put("MEMBER", List.of("member", "user", "account", "회원", "사용자", "계정"));
		DOMAIN_KEYWORDS.put("AUTH", List.of("auth", "login", "token", "jwt", "인증", "로그인", "권한"));
		DOMAIN_KEYWORDS.put("NOTIFICATION", List.of("notification", "alarm", "email", "sms", "알림", "메일"));
	}

	public StructuredBugReport structure(BugReport report) {
		String text = (report.getTitle() + " " + report.getDescription()).toLowerCase(Locale.ROOT);
		List<String> keywords = extractKeywords(text);
		List<String> domains = extractDomains(text);
		IssueType issueType = classifyIssueType(text);
		return new StructuredBugReport(keywords, domains, issueType);
	}

	private List<String> extractKeywords(String text) {
		String[] tokens = text.split("[^\\p{IsAlphabetic}\\p{IsDigit}_가-힣]+");
		Set<String> keywords = new LinkedHashSet<>();
		for (String token : tokens) {
			String keyword = token.strip();
			if (keyword.length() >= 2 && !STOP_WORDS.contains(keyword)) {
				keywords.add(keyword);
			}
			if (keywords.size() >= 12) {
				break;
			}
		}
		return new ArrayList<>(keywords);
	}

	private List<String> extractDomains(String text) {
		List<String> domains = new ArrayList<>();
		for (Map.Entry<String, List<String>> entry : DOMAIN_KEYWORDS.entrySet()) {
			boolean matched = entry.getValue().stream().anyMatch(text::contains);
			if (matched) {
				domains.add(entry.getKey());
			}
		}
		return domains;
	}

	private IssueType classifyIssueType(String text) {
		if (containsAny(text, "장애", "incident", "outage", "down", "실패", "fail")) {
			return IssueType.INCIDENT;
		}
		if (containsAny(text, "개선", "improve", "refactor", "slow", "느림")) {
			return IssueType.IMPROVEMENT;
		}
		if (containsAny(text, "기능", "feature", "request", "추가")) {
			return IssueType.FEATURE_REQUEST;
		}
		if (containsAny(text, "bug", "버그", "오류", "에러", "error", "exception", "문제")) {
			return IssueType.BUG;
		}
		return IssueType.UNKNOWN;
	}

	private boolean containsAny(String text, String... candidates) {
		for (String candidate : candidates) {
			if (text.contains(candidate)) {
				return true;
			}
		}
		return false;
	}
}
