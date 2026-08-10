package ax.clio.common.idempotency;

import ax.clio.common.ConflictException;

public class IdempotencyConflictException extends ConflictException {

	public IdempotencyConflictException(String message) {
		super(message);
	}
}
