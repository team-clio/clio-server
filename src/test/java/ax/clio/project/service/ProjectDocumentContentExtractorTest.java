package ax.clio.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ax.clio.common.UnprocessableContentException;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;

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

	@Test
	void extractsTextFromPdf() throws Exception {
		assertThat(extractor.extract("architecture.pdf", pdf("Architecture decision record")))
				.contains("Architecture decision record");
	}

	private byte[] pdf(String text) throws Exception {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			document.addPage(new PDPage());
			try (PDPageContentStream stream = new PDPageContentStream(document, document.getPage(0))) {
				stream.beginText();
				stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
				stream.newLineAtOffset(72, 720);
				stream.showText(text);
				stream.endText();
			}
			document.save(output);
			return output.toByteArray();
		}
	}
}
