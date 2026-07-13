package ax.clio.code.dto;

import ax.clio.code.entity.CodeSymbol;
import ax.clio.code.entity.CodeSymbolType;

public record CodeSymbolResponse(
		Long id,
		Long fileId,
		String filePath,
		String name,
		CodeSymbolType type,
		String role,
		String packageName,
		Integer startLine,
		Integer endLine,
		String annotations,
		String imports
) {

	public static CodeSymbolResponse from(CodeSymbol symbol) {
		return new CodeSymbolResponse(
				symbol.getId(),
				symbol.getFile().getId(),
				symbol.getFile().getPath(),
				symbol.getName(),
				symbol.getType(),
				symbol.getRole(),
				symbol.getPackageName(),
				symbol.getStartLine(),
				symbol.getEndLine(),
				symbol.getAnnotations(),
				symbol.getImports()
		);
	}
}
