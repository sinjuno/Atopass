import { ApiError } from '@/apis/client';
import type { ErrorCode } from '@/types/api';

const DEFAULT_MESSAGE = '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.';

/** error.code 별 화면 문구 (화면 명세서 3. 오류 표시). 없는 코드는 서버 message 를 쓴다. */
const MESSAGES: Partial<Record<ErrorCode, string>> = {
  INVALID_INPUT: '입력한 내용을 다시 확인해 주세요.',
  UNAUTHORIZED: '로그인이 필요해요.',
  FORBIDDEN: '접근할 수 없어요.',
  INTERNAL_ERROR: DEFAULT_MESSAGE,
  INVALID_CREDENTIALS: '이메일 또는 비밀번호가 올바르지 않아요.',
  TOKEN_EXPIRED: '로그인이 만료됐어요. 다시 로그인해 주세요.',
  EMAIL_DUPLICATED: '이미 가입된 이메일이에요.',
  RECORD_NOT_FOUND: '기록을 찾을 수 없어요.',
  REPORT_NOT_FOUND: '보고서를 찾을 수 없어요.',
  NO_RECORDS_IN_PERIOD: '선택한 기간에 기록이 없어요.',
};

export function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === undefined) return '네트워크 연결을 확인해 주세요.';
    return (error.code && MESSAGES[error.code]) || error.message || DEFAULT_MESSAGE;
  }
  return DEFAULT_MESSAGE;
}

/** 검증 오류(error.details)를 { field: reason } 으로 바꿔 입력칸 옆에 표시한다. */
export function getFieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError)) return {};
  return Object.fromEntries(error.details.map((d) => [d.field, d.reason]));
}
