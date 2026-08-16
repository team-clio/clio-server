package ax.clio.common;

import java.time.Instant;

import ax.clio.common.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNotFound(
			ResourceNotFoundException exception,
			HttpServletRequest request
	) {
		return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage(), request);
	}

	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<ApiErrorResponse> handleConflict(
			ConflictException exception,
			HttpServletRequest request
	) {
		return response(HttpStatus.CONFLICT, "CONFLICT", exception.getMessage(), request);
	}

	@ExceptionHandler(PcmInspectUnavailableException.class)
	public ResponseEntity<ApiErrorResponse> handlePcmInspectUnavailable(
			PcmInspectUnavailableException exception,
			HttpServletRequest request
	) {
		return response(
				HttpStatus.SERVICE_UNAVAILABLE,
				"PCM_INSPECT_UNAVAILABLE",
				exception.getMessage(),
				request
		);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(
			MethodArgumentNotValidException exception,
			HttpServletRequest request
	) {
		String message = exception.getBindingResult().getAllErrors().stream()
				.findFirst()
				.map(error -> error.getDefaultMessage())
				.orElse("Request validation failed.");
		return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, request);
	}

	@ExceptionHandler({
			IllegalArgumentException.class,
			ConstraintViolationException.class,
			MethodArgumentTypeMismatchException.class,
			HttpMessageNotReadableException.class
	})
	public ResponseEntity<ApiErrorResponse> handleBadRequest(
			Exception exception,
			HttpServletRequest request
	) {
		return response(
				HttpStatus.BAD_REQUEST,
				"INVALID_REQUEST",
				exception.getMessage(),
				request
		);
	}

	private ResponseEntity<ApiErrorResponse> response(
			HttpStatus status,
			String code,
			String message,
			HttpServletRequest request
	) {
		return ResponseEntity.status(status).body(new ApiErrorResponse(
				Instant.now(),
				status.value(),
				code,
				message,
				request.getRequestURI()
		));
	}
}
