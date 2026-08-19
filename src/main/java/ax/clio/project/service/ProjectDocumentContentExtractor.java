package ax.clio.project.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import ax.clio.common.UnprocessableContentException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class ProjectDocumentContentExtractor {

	public String extract(String originalFilename, byte[] bytes) {
		String extension = extensionOf(originalFilename);
		if ("md".equals(extension) || "markdown".equals(extension)) {
			return requireContent(new String(bytes, StandardCharsets.UTF_8));
		}
		if ("pdf".equals(extension)) {
			return extractPdf(bytes);
		}
		throw new UnprocessableContentException("Only PDF and Markdown files are supported.");
	}

	private String extractPdf(byte[] bytes) {
		try (var document = Loader.loadPDF(bytes)) {
			return requireContent(new PDFTextStripper().getText(document));
		} catch (IOException exception) {
			throw new UnprocessableContentException("PDF text could not be extracted.", exception);
		}
	}

	private String requireContent(String content) {
		String normalized = content.replace("\r\n", "\n").replace('\r', '\n').trim();
		if (normalized.isEmpty()) {
			throw new UnprocessableContentException("Document has no readable text.");
		}
		return normalized;
	}

	private String extensionOf(String filename) {
		if (filename == null || filename.isBlank()) {
			throw new UnprocessableContentException("A filename is required.");
		}
		int dot = filename.lastIndexOf('.');
		return dot < 1 || dot == filename.length() - 1 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
	}
}
