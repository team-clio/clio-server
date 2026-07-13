package ax.clio.memory.code;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import ax.clio.code.entity.CodeFile;
import ax.clio.code.entity.CodeSymbol;
import ax.clio.code.entity.CodeSymbolType;
import ax.clio.project.entity.Project;
import org.springframework.stereotype.Component;

/**
 * 스캔 결과({@link CodeSymbol})를 chunk({@link CodeChunk})로 변환한다.
 *
 * <p>D1: 메서드 단위(+클래스 헤더). METHOD/CONSTRUCTOR 심볼 → METHOD chunk(본문 라인 범위),
 * 타입 심볼 → CLASS_HEADER chunk(선언+필드, 첫 메서드 직전까지). {@link CodeSymbol}에 부모 링크가
 * 없으므로 startLine/endLine 포함 관계로 감싸는 타입을 판정한다(역할 상속·헤더 경계).
 *
 * <p>embedding은 여기서 계산하지 않는다(null). 스캔 파이프라인의 embed 단계(S3/S4)에서 부착한다.
 */
@Component
public class CodeChunker {

	public List<CodeChunk> chunk(Project project, CodeFile file, List<String> sourceLines, List<CodeSymbol> symbols) {
		List<CodeSymbol> types = symbols.stream()
				.filter(symbol -> isType(symbol.getType()))
				.filter(CodeChunker::hasRange)
				.toList();
		List<CodeSymbol> methods = symbols.stream()
				.filter(symbol -> symbol.getType() == CodeSymbolType.METHOD
						|| symbol.getType() == CodeSymbolType.CONSTRUCTOR)
				.filter(CodeChunker::hasRange)
				.toList();

		List<CodeChunk> chunks = new ArrayList<>();

		for (CodeSymbol type : types) {
			int headerEnd = classHeaderEndLine(type, methods);
			String content = slice(sourceLines, type.getStartLine(), headerEnd);
			chunks.add(new CodeChunk(project, file, file.getPath(), type.getName(), type.getRole(),
					CodeChunkType.CLASS_HEADER, type.getStartLine(), headerEnd, content, null));
		}

		for (CodeSymbol method : methods) {
			CodeSymbol enclosing = enclosingType(method, types);
			String role = enclosing != null ? enclosing.getRole() : null;
			String content = slice(sourceLines, method.getStartLine(), method.getEndLine());
			chunks.add(new CodeChunk(project, file, file.getPath(), method.getName(), role,
					CodeChunkType.METHOD, method.getStartLine(), method.getEndLine(), content, null));
		}

		return chunks;
	}

	/** 클래스 헤더는 타입 선언부터 이 타입에 직접 속한 첫 메서드 직전까지(선언+필드). 메서드가 없으면 타입 끝까지. */
	private int classHeaderEndLine(CodeSymbol type, List<CodeSymbol> methods) {
		return methods.stream()
				.filter(method -> enclosesDirectly(type, method, methods))
				.map(CodeSymbol::getStartLine)
				.min(Comparator.naturalOrder())
				.map(firstMethodStart -> Math.max(type.getStartLine(), firstMethodStart - 1))
				.orElse(type.getEndLine());
	}

	private boolean enclosesDirectly(CodeSymbol type, CodeSymbol method, List<CodeSymbol> methods) {
		// method가 이 type에 직접 속하는지: type이 감싸고, 더 안쪽 타입이 없어야 하나 CodeSymbol엔 타입 트리가
		// 없으므로 라인 포함으로 근사한다. 헤더 경계 계산엔 "이 타입 범위 안의 첫 메서드"면 충분.
		return encloses(type, method);
	}

	private CodeSymbol enclosingType(CodeSymbol method, List<CodeSymbol> types) {
		return types.stream()
				.filter(type -> encloses(type, method))
				.min(Comparator.comparingInt(type -> type.getEndLine() - type.getStartLine()))
				.orElse(null);
	}

	private static boolean encloses(CodeSymbol outer, CodeSymbol inner) {
		return outer.getStartLine() <= inner.getStartLine() && outer.getEndLine() >= inner.getEndLine();
	}

	private static boolean isType(CodeSymbolType type) {
		return type == CodeSymbolType.CLASS
				|| type == CodeSymbolType.INTERFACE
				|| type == CodeSymbolType.ENUM
				|| type == CodeSymbolType.RECORD
				|| type == CodeSymbolType.ANNOTATION;
	}

	private static boolean hasRange(CodeSymbol symbol) {
		return symbol.getStartLine() != null && symbol.getEndLine() != null;
	}

	/** 1-based [startLine, endLine] 라인을 잘라 이어붙인다. */
	private static String slice(List<String> lines, int startLine, int endLine) {
		int from = Math.max(1, startLine);
		int to = Math.min(lines.size(), endLine);
		if (from > to) {
			return "";
		}
		return String.join("\n", lines.subList(from - 1, to));
	}
}
