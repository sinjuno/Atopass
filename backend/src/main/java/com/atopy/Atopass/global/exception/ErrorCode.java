package com.atopy.Atopass.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

	// 공통
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 요청 데이터입니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

	// 인증 / 회원
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
	TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었거나 유효하지 않습니다."),
	EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),

	// 기록
	RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 기록을 찾을 수 없습니다."),
	RECORD_ALREADY_EXISTS(HttpStatus.CONFLICT, "해당 날짜의 기록이 이미 존재합니다."),
	INVALID_SCORE_RANGE(HttpStatus.BAD_REQUEST, "증상 점수는 0~10 범위여야 합니다."),

	// 사진
	INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "허용되지 않는 파일 형식입니다."),
	FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "파일 크기가 허용 범위를 초과했습니다."),
	PHOTO_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "기록당 사진 수를 초과했습니다."),

	// 챗봇
	SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "대화 세션을 찾을 수 없습니다."),
	SESSION_CLOSED(HttpStatus.CONFLICT, "이미 기록이 저장된 대화입니다."),
	CHATBOT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "챗봇을 일시적으로 사용할 수 없습니다."),
	DRAFT_NOT_CONFIRMED(HttpStatus.BAD_REQUEST, "확인되지 않은 기록 초안은 저장할 수 없습니다."),
	CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "챗봇을 이용하려면 동의가 필요합니다."),
	CHAT_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "챗봇 이용 한도에 도달했습니다. 잠시 후 다시 시도하거나 직접 입력해 주세요."),

	// 보고서
	REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 보고서를 찾을 수 없습니다."),
	NO_RECORDS_IN_PERIOD(HttpStatus.BAD_REQUEST, "선택한 기간에 기록이 없습니다.");

	private final HttpStatus status;
	private final String message;

	ErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

}
