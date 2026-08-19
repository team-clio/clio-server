package ax.clio.common;

public class UnprocessableContentException extends RuntimeException {

	public UnprocessableContentException(String message) {
		super(message);
	}

	public UnprocessableContentException(String message, Throwable cause) {
		super(message, cause);
	}
}
