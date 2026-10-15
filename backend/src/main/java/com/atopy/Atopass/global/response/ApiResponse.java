package com.atopy.Atopass.global.response;

import lombok.Getter;

@Getter
public class ApiResponse<T> {

	private static final String DEFAULT_MESSAGE = "요청이 성공했습니다.";

	private final boolean success = true;
	private final T data;
	private final String message;

	private ApiResponse(T data, String message) {
		this.data = data;
		this.message = message;
	}

	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(data, DEFAULT_MESSAGE);
	}

	public static <T> ApiResponse<T> ok(T data, String message) {
		return new ApiResponse<>(data, message);
	}

}
