package ax.clio.common;

/**
 * standalone PCM inspect 서버가 5xx를 반환하거나 연결할 수 없을 때 사용한다.
 */
public class PcmInspectUnavailableException extends RuntimeException {

	public PcmInspectUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
