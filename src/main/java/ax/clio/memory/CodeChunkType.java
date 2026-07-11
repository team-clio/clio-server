package ax.clio.memory;

/**
 * chunk 경계 종류 (D1: 메서드 단위 + 클래스 헤더).
 */
public enum CodeChunkType {
	/** 타입 선언 + 필드 등 클래스 헤더(메서드 본문 제외). */
	CLASS_HEADER,
	/** 메서드/생성자 본문. */
	METHOD
}
