package ax.clio.memory.issue;

import ax.clio.memory.embedding.LocalEmbeddingClient;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import ax.clio.project.entity.Project;
import ax.clio.report.entity.BugReport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/**
 * 전 구간(S1~S4): 분석된 리포트를 remember → 새 리포트로 findSimilar가 유사 과거 이슈를 반환하고 자기 자신을 제외.
 * 로컬 embedding(한글) + in-memory 코사인으로 pgvector 없이 H2에서 배선 검증(I7).
 */
@DataJpaTest
class IssueMemoryIntegrationTest {

	@Autowired
	private IssueEmbeddingRepository repository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void remembersAndFindsSimilarPastIssueExcludingSelf() {
		Project project = new Project("demo", "/workspace/demo", "desc");
		entityManager.persist(project);
		BugReport payment = report(project, "결제 취소 오류", "결제 취소 시 결제가 취소되지 않음");
		BugReport login = report(project, "로그인 세션 만료", "로그인 후 세션이 만료되어 튕김");
		BugReport query = report(project, "결제 취소 안됨", "결제 취소 버튼이 결제를 취소 안함");
		entityManager.flush();

		IssueMemoryService service = new IssueMemoryService(
				new LocalEmbeddingClient(), repository, new InMemoryIssueVectorSearch(repository));

		service.remember(payment);
		service.remember(login);
		service.remember(query);
		entityManager.flush();

		List<ScoredIssue> similar = service.findSimilar(query, 1);

		assertThat(similar).hasSize(1);
		// 자기 자신(query) 제외, 토큰 겹치는 payment가 login보다 상위
		assertThat(similar.get(0).issue().getReport().getId()).isEqualTo(payment.getId());
		assertThat(similar).noneSatisfy(scored ->
				assertThat(scored.issue().getReport().getId()).isEqualTo(query.getId()));
	}

	private BugReport report(Project project, String title, String description) {
		BugReport report = new BugReport(project, title, description);
		entityManager.persist(report);
		return report;
	}
}
