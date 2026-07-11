package ax.clio.memory;

import static org.assertj.core.api.Assertions.assertThat;

import ax.clio.project.Project;
import ax.clio.report.BugReport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/**
 * S1 검증: H2에서 {@code issue_embeddings} 테이블(배열 컬럼 포함) DDL이 서고 저장·조회된다.
 * pgvector 유사도 쿼리는 여기서 검증하지 않는다(I7: 별도 벤치마크).
 */
@DataJpaTest
class IssueEmbeddingRepositoryTest {

	@Autowired
	private IssueEmbeddingRepository repository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void savesAndReadsBackWithEmbeddingArrayOnH2() {
		Project project = new Project("demo", "/workspace/demo", "desc");
		entityManager.persist(project);
		BugReport report = new BugReport(project, "결제 취소 오류", "결제를 취소하면 NPE가 납니다");
		entityManager.persist(report);

		repository.save(new IssueEmbedding(project, report, report.getTitle(), new float[] {0.1f, 0.2f, 0.3f}));
		entityManager.flush();
		entityManager.clear();

		IssueEmbedding found = repository.findByProjectId(project.getId()).get(0);
		assertThat(found.getTitleSnapshot()).isEqualTo("결제 취소 오류");
		assertThat(found.getEmbedding()).containsExactly(0.1f, 0.2f, 0.3f);
		assertThat(repository.countByProjectId(project.getId())).isEqualTo(1);
	}

	@Test
	void deleteByReportIdRemovesOnlyThatReport() {
		Project project = new Project("demo", "/workspace/demo", "desc");
		entityManager.persist(project);
		BugReport a = new BugReport(project, "A", "aaa");
		BugReport b = new BugReport(project, "B", "bbb");
		entityManager.persist(a);
		entityManager.persist(b);
		repository.save(new IssueEmbedding(project, a, "A", new float[] {1f}));
		repository.save(new IssueEmbedding(project, b, "B", new float[] {2f}));
		entityManager.flush();

		repository.deleteByReportId(a.getId());
		entityManager.flush();
		entityManager.clear();

		assertThat(repository.countByProjectId(project.getId())).isEqualTo(1);
		assertThat(repository.findByProjectId(project.getId()).get(0).getTitleSnapshot()).isEqualTo("B");
	}
}
