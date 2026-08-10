package ax.clio.common.idempotency;

public record IdempotentResponse<T>(
		T body,
		int httpStatus,
		boolean replayed
) {
}
