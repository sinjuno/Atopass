// 공통 응답 형식 (API 명세서 1. 공통 규칙)
export interface ApiSuccess<T> {
  success: true;
  data: T;
  message?: string;
}

export interface ErrorDetail {
  field: string;
  reason: string;
}

export interface ApiErrorBody {
  success: false;
  error: {
    code: ErrorCode;
    message: string;
    details?: ErrorDetail[];
  };
}

export type ErrorCode =
  // 공통
  | 'INVALID_INPUT'
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'INTERNAL_ERROR'
  // 인증/회원
  | 'INVALID_CREDENTIALS'
  | 'TOKEN_EXPIRED'
  | 'EMAIL_DUPLICATED'
  // 기록
  | 'RECORD_NOT_FOUND'
  | 'RECORD_ALREADY_EXISTS'
  | 'INVALID_SCORE_RANGE'
  // 사진
  | 'INVALID_FILE_TYPE'
  | 'FILE_TOO_LARGE'
  | 'PHOTO_LIMIT_EXCEEDED'
  // 챗봇
  | 'SESSION_NOT_FOUND'
  | 'SESSION_CLOSED'
  | 'CHATBOT_UNAVAILABLE'
  | 'CHAT_LIMIT_EXCEEDED'
  | 'DRAFT_NOT_CONFIRMED'
  | 'CONSENT_REQUIRED'
  // 보고서
  | 'REPORT_NOT_FOUND'
  | 'NO_RECORDS_IN_PERIOD';
