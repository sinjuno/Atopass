package com.atopy.Skinlog.global.response;

import com.atopy.Skinlog.global.exception.ErrorCode;

import java.util.List;

// TEMP STUB — 백1 공통 모듈 머지 시 교체
public record ErrorResponse(boolean success, ErrorBody error) {

    public record ErrorBody(String code, String message, List<ErrorDetail> details) {
    }

    public static ErrorResponse of(ErrorCode code, List<ErrorDetail> details) {
        return new ErrorResponse(false, new ErrorBody(code.name(), code.getMessage(), details));
    }
}
