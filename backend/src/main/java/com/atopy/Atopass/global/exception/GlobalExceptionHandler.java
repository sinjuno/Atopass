package com.atopy.Atopass.global.exception;

import com.atopy.Atopass.global.response.ErrorDetail;
import com.atopy.Atopass.global.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	/** 증상 점수 범위 제약(@Score)의 검증 코드 */
	private static final String SCORE_CONSTRAINT = "Score";

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
		ErrorCode code = e.getErrorCode();
		return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code, e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
		List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();
		List<ErrorDetail> details = fieldErrors.stream()
			.map(f -> new ErrorDetail(f.getField(), f.getDefaultMessage()))
			.toList();
		// 모든 위반이 증상 점수 범위(@Score)라면 INVALID_SCORE_RANGE로 응답한다.
		boolean onlyScoreErrors = !fieldErrors.isEmpty()
			&& fieldErrors.stream().allMatch(f -> SCORE_CONSTRAINT.equals(f.getCode()));
		return onlyScoreErrors ? error(ErrorCode.INVALID_SCORE_RANGE, details) : invalidInput(details);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e) {
		List<ErrorDetail> details = e.getParameterValidationResults().stream()
			.flatMap(result -> result.getResolvableErrors().stream()
				.map(err -> new ErrorDetail(result.getMethodParameter().getParameterName(),
					err.getDefaultMessage())))
			.toList();
		return invalidInput(details);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
		List<ErrorDetail> details = e.getConstraintViolations().stream()
			.map(v -> new ErrorDetail(v.getPropertyPath().toString(), v.getMessage()))
			.toList();
		return invalidInput(details);
	}

	@ExceptionHandler({
		HttpMessageNotReadableException.class,
		MethodArgumentTypeMismatchException.class,
		MissingServletRequestParameterException.class
	})
	public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
		return invalidInput(List.of());
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ErrorResponse> handleMaxUpload(MaxUploadSizeExceededException e) {
		ErrorCode code = ErrorCode.FILE_TOO_LARGE;
		return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
		ErrorCode code = ErrorCode.FORBIDDEN;
		return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
		ErrorCode code = ErrorCode.NOT_FOUND;
		return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
		ErrorCode code = ErrorCode.METHOD_NOT_ALLOWED;
		return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
		log.error("처리되지 않은 예외", e);
		ErrorCode code = ErrorCode.INTERNAL_ERROR;
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.of(code));
	}

	private ResponseEntity<ErrorResponse> invalidInput(List<ErrorDetail> details) {
		return error(ErrorCode.INVALID_INPUT, details);
	}

	private ResponseEntity<ErrorResponse> error(ErrorCode code, List<ErrorDetail> details) {
		return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code, details));
	}

}
