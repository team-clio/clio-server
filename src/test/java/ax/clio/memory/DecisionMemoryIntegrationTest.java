package ax.clio.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import ax.clio.project.Project;
import ax.clio.project.ProjectRepository;
import ax.clio.project.ProjectService;
import ax.clio.report.BugReport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/**
 * 전 구간(S2~S5): 결정 메모 register → 새 리포트로 findRelevant가 관련 결정을 반환하고 프로젝트 밖은 제외.
 * 로컬 embedding(한글) + in-memory 코사인으로 pgvector 없이 H2에서 배선 검증(D8).
 */
@DataJpaTest
class DecisionMemoryIntegrationTest {

	@Autowired
	private DecisionMemoryRepository decisionRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void registersAndFindsRelevantDecisionWithinProject() {
		Project project = persistProject("demo", "/workspace/demo");
		Project other = persistProject("other", "/workspace/other");

		DecisionMemoryService service = new DecisionMemoryService(
				new LocalEmbeddingClient(), decisionRepository,
				new InMemoryDecisionVectorSearch(decisionRepository),
				new ProjectService(projectRepository, null));

		service.register(project.getId(), "결제 트랜잭션 의도적 제외",
				"결제 조회 경로는 성능 때문에 트랜잭션을 걸지 않는다");
		service.register(project.getId(), "로그인 세션 정책",
				"로그인 세션은 30분 후 만료한다");
		// 같은 내용이지만 다른 프로젝트 → 스코프 밖이라 검색되면 안 됨
		service.register(other.getId(), "결제 트랜잭션 의도적 제외",
				"결제 조회 경로는 성능 때문에 트랜잭션을 걸지 않는다");
		entityManager.flush();

		BugReport report = new BugReport(project, "결제 트랜잭션 오류",
				"결제 조회 시 트랜잭션 관련 오류가 발생");

		List<ScoredDecision> relevant = service.findRelevant(report, 1);

		assertThat(relevant).hasSize(1);
		// 토큰 겹치는 결제-트랜잭션 결정이 로그인 결정보다 상위, 프로젝트 스코프 유지
		assertThat(relevant.get(0).decision().getTitle()).isEqualTo("결제 트랜잭션 의도적 제외");
		assertThat(relevant.get(0).decision().getProject().getId()).isEqualTo(project.getId());
	}

	@Test
	void findRelevantReturnsEmptyWhenNoDecisions() {
		Project project = persistProject("empty", "/workspace/empty");
		DecisionMemoryService service = new DecisionMemoryService(
				new LocalEmbeddingClient(), decisionRepository,
				new InMemoryDecisionVectorSearch(decisionRepository),
				new ProjectService(projectRepository, null));

		BugReport report = new BugReport(project, "무엇이든", "관련 결정이 없는 프로젝트");

		assertThat(service.findRelevant(report, 3)).isEmpty();
	}

	private Project persistProject(String name, String path) {
		Project project = new Project(name, path, "desc");
		entityManager.persist(project);
		return project;
	}
}
