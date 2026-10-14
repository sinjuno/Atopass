package com.atopy.Skinlog.global.exception;

import com.atopy.Skinlog.global.response.ErrorDetail;

import java.util.Collections;
import java.util.List;

// TEMP STUB — 백1 공통 모듈 머지 시 교체
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<ErrorDetail> details;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, Collections.emptyList());
    }

    public BusinessException(ErrorCode errorCode, List<ErrorDetail> details) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = details;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public List<ErrorDetail> getDetails() {
        return details;
    }
}
