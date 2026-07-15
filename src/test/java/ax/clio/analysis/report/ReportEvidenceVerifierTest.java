package ax.clio.analysis.report;

import ax.clio.analysis.pipeline.contract.CodeFlow;
import ax.clio.analysis.pipeline.contract.FlowNode;
import ax.clio.analysis.pipeline.contract.GeneratedReport;
import ax.clio.analysis.pipeline.contract.RelatedCodeEntry;
import ax.clio.analysis.report.ReportEvidenceVerifier;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 근거 검증기(roadmap #11 / 4.4). 근거 안 언급은 통과, 근거 밖 파일/클래스는 경고, 정상 한글·단일 대문자
 * 단어는 오탐 없음(보수적 추출). 참조 검증이지 의미 검증이 아님(E2·E3).
 */
class ReportEvidenceVerifierTest {

	private final ReportEvidenceVerifier verifier = new ReportEvidenceVerifier();

	private final List<RelatedCodeEntry> relatedCode = List.of(
			new RelatedCodeEntry("src/main/java/ax/PaymentService.java", "PaymentService", "SERVICE", "EXACT", 42, "s", 90, 3));
	private final List<CodeFlow> flows = List.of(new CodeFlow(List.of(
			new FlowNode("src/main/java/ax/PaymentController.java", "PaymentController", "CONTROLLER", false),
			new FlowNode("src/main/java/ax/PaymentService.java", "PaymentService", "SERVICE", false))));

	@Test
	void passesWhenMentionsAreInEvidence() {
		GeneratedReport report = new GeneratedReport(
				"PaymentController에서 PaymentService로 이어지는 흐름을 확인하세요.",
				"PaymentService.java의 취소 처리를 점검하세요.",  // .java 표기도 클래스와 같은 근거로 취급
				"결제 취소 통합 테스트를 추가하세요.");

		assertThat(verifier.findUnsupportedReferences(report, relatedCode, flows)).isEmpty();
	}

	@Test
	void flagsClassNotInEvidence() {
		GeneratedReport report = new GeneratedReport(
				"PaymentService에서 OrderValidator를 호출하는 부분을 확인하세요.",  // OrderValidator는 근거 밖
				"수정 방향", "테스트 전략");

		List<String> warnings = verifier.findUnsupportedReferences(report, relatedCode, flows);

		assertThat(warnings).containsExactly("근거 없는 코드 언급: OrderValidator");
	}

	@Test
	void flagsJavaFileNotInEvidence() {
		GeneratedReport report = new GeneratedReport(
				"OrderRepository.java를 확인하세요.", "fix", "tests");

		// 파일명 언급도 클래스명 형태로 통일해 표시(.java 제거)
		assertThat(verifier.findUnsupportedReferences(report, relatedCode, flows))
				.containsExactly("근거 없는 코드 언급: OrderRepository");
	}

	@Test
	void doesNotFlagPlainKoreanOrSingleCapitalWords() {
		GeneratedReport report = new GeneratedReport(
				"결제 취소 시 상태가 갱신되지 않습니다. Payment 도메인을 확인하세요.",  // 'Payment'=언덕1, 한글=대상 아님
				"서비스 계층의 예외 처리를 점검하세요.",
				"통합 테스트와 단위 테스트를 추가하세요.");

		assertThat(verifier.findUnsupportedReferences(report, relatedCode, flows)).isEmpty();
	}

	@Test
	void safeOnEmptyEvidenceAndText() {
		GeneratedReport empty = new GeneratedReport("", "", "");
		assertThat(verifier.findUnsupportedReferences(empty, List.of(), List.of())).isEmpty();
		assertThat(verifier.findUnsupportedReferences(null, relatedCode, flows)).isEmpty();
	}

	@Test
	void deduplicatesRepeatedUnsupportedMention() {
		GeneratedReport report = new GeneratedReport(
				"OrderValidator 확인. OrderValidator.java 도 확인.", "OrderValidator 재확인", "tests");

		assertThat(verifier.findUnsupportedReferences(report, relatedCode, flows))
				.containsExactly("근거 없는 코드 언급: OrderValidator");
	}
}
