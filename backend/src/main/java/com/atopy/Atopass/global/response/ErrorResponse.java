package com.atopy.Atopass.global.response;

import com.atopy.Atopass.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Getter;

@Getter
public class ErrorResponse {

	private final boolean success = false;
	private final Error error;

	private ErrorResponse(Error error) {
		this.error = error;
	}

	public static ErrorResponse of(ErrorCode errorCode) {
		return new ErrorResponse(new Error(errorCode.name(), errorCode.getMessage(), null));
	}

	public static ErrorResponse of(ErrorCode errorCode, String message) {
		return new ErrorResponse(new Error(errorCode.name(), message, null));
	}

	public static ErrorResponse of(ErrorCode errorCode, List<ErrorDetail> details) {
		return new ErrorResponse(new Error(errorCode.name(), errorCode.getMessage(), details));
	}

	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	public record Error(String code, String message, List<ErrorDetail> details) {
	}

}
