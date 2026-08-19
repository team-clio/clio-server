package ax.clio.project.entity;

import static org.assertj.core.api.Assertions.assertThat;

import ax.clio.project.repository.ProjectContextRepository;
import ax.clio.project.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class ProjectContextTest {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private ProjectContextRepository projectContextRepository;

	@Test
	void storesNormalizedDocumentAndFindsItByProjectAndContentHash() {
		Project project = projectRepository.save(Project.create("Clio", null));
		ProjectContext document = projectContextRepository.save(ProjectContext.createDocument(
				project, "Architecture", "# Architecture", "architecture.md",
				"text/markdown", "sha256:abc"
		));

		assertThat(projectContextRepository.findByProjectIdAndContentHash(project.getId(), "sha256:abc"))
				.contains(document);
		assertThat(document.getType()).isEqualTo(ProjectContextType.ETC);
	}
}
