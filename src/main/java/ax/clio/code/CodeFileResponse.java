package ax.clio.code;

import java.time.Instant;

public record CodeFileResponse(
		Long id,
		String path,
		String fileName,
		String language,
		boolean test,
		long sizeBytes,
		Instant lastModifiedAt
) {

	public static CodeFileResponse from(CodeFile codeFile) {
		return new CodeFileResponse(
				codeFile.getId(),
				codeFile.getPath(),
				codeFile.getFileName(),
				codeFile.getLanguage(),
				codeFile.isTest(),
				codeFile.getSizeBytes(),
				codeFile.getLastModifiedAt()
		);
	}
}
