package com.atopy.Skinlog.global.exception;

public enum ErrorCode {

    INVALID_INPUT(400, "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(401, "인증이 필요합니다."),
    FORBIDDEN(403, "접근 권한이 없습니다."),
    INTERNAL_ERROR(500, "서버 오류가 발생했습니다."),
    INVALID_CREDENTIALS(401, "이메일 또는 비밀번호가 일치하지 않습니다."),
    TOKEN_EXPIRED(401, "토큰이 만료되었습니다."),
    EMAIL_DUPLICATED(409, "이미 가입된 이메일입니다."),
    RECORD_NOT_FOUND(404, "해당 기록을 찾을 수 없습니다."),
    RECORD_ALREADY_EXISTS(409, "같은 날짜의 기록이 이미 존재합니다."),
    INVALID_SCORE_RANGE(400, "점수는 0~10 범위여야 합니다."),
    INVALID_FILE_TYPE(400, "허용되지 않는 파일 형식입니다."),
    FILE_TOO_LARGE(413, "파일 크기가 너무 큽니다."),
    PHOTO_LIMIT_EXCEEDED(409, "기록당 사진 수를 초과했습니다."),
    SESSION_NOT_FOUND(404, "세션을 찾을 수 없습니다."),
    SESSION_CLOSED(409, "이미 종료된 세션입니다."),
    CHATBOT_UNAVAILABLE(503, "챗봇 처리 중 오류가 발생했습니다."),
    DRAFT_NOT_CONFIRMED(400, "확인되지 않은 초안입니다."),
    REPORT_NOT_FOUND(404, "보고서를 찾을 수 없습니다."),
    NO_RECORDS_IN_PERIOD(400, "선택한 기간에 기록이 없습니다.");

    private final int status;
    private final String message;

    ErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public int getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
