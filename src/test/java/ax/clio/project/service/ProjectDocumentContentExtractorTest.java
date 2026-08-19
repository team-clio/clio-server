package ax.clio.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ax.clio.common.UnprocessableContentException;
import org.junit.jupiter.api.Test;

class ProjectDocumentContentExtractorTest {

	private final ProjectDocumentContentExtractor extractor = new ProjectDocumentContentExtractor();

	@Test
	void normalizesMarkdown() {
		assertThat(extractor.extract("requirements.MD", "\r\n# Requirements\r\n".getBytes()))
				.isEqualTo("# Requirements");
	}

	@Test
	void rejectsUnsupportedOrBlankDocuments() {
		assertThatThrownBy(() -> extractor.extract("requirements.txt", "text".getBytes()))
				.isInstanceOf(UnprocessableContentException.class);
		assertThatThrownBy(() -> extractor.extract("requirements.md", "  ".getBytes()))
				.isInstanceOf(UnprocessableContentException.class);
	}
}
