package com.atopy.Skinlog.global.response;

// TEMP STUB — 백1 공통 모듈 머지 시 교체
public record ApiResponse<T>(boolean success, String message, T data) {

    private static final String DEFAULT_MESSAGE = "요청이 성공했습니다.";

    public static <T> ApiResponse<T> of(T data) {
        return of(DEFAULT_MESSAGE, data);
    }

    public static <T> ApiResponse<T> of(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }
}
