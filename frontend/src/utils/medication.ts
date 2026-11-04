import { ApiError } from '@/apis/client';
import type { MedicationTime, MedicationType } from '@/types/medication';
import { getErrorMessage } from './errorMessage';

export const MED_PHOTO_MAX_COUNT = 3;
export const MED_PHOTO_MAX_BYTES = 10 * 1024 * 1024;

export const MEDICATION_TYPES: { value: MedicationType; label: string }[] = [
  { value: 'oral', label: '먹는 약' },
  { value: 'topical', label: '바르는 약' },
  { value: 'other', label: '기타' },
];

export const MEDICATION_TIMES: { value: MedicationTime; label: string }[] = [
  { value: 'morning', label: '아침' },
  { value: 'lunch', label: '점심' },
  { value: 'evening', label: '저녁' },
  { value: 'bedtime', label: '자기 전' },
];

export const typeLabel = (type: MedicationType) => MEDICATION_TYPES.find((t) => t.value === type)?.label ?? type;
export const timeLabel = (time: MedicationTime) => MEDICATION_TIMES.find((t) => t.value === time)?.label ?? time;

export const PHOTO_TOO_MANY_MESSAGE = '사진은 3장, 장당 10MB까지 올릴 수 있어요.';

/** 올리기 전 안내용 검증 — 서버(413)도 다시 검증한다. */
export function validateMedicationPhotos(files: File[]): string | null {
  if (files.length > MED_PHOTO_MAX_COUNT || files.some((f) => f.size > MED_PHOTO_MAX_BYTES)) {
    return PHOTO_TOO_MANY_MESSAGE;
  }
  return null;
}

/** 공통 약속의 에러 표 문구 그대로. extract 외의 요청에는 413·422·429 가 없다. */
export function medicationErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 413) return PHOTO_TOO_MANY_MESSAGE;
    if (error.status === 422) return '잘 안 보여요. 다시 찍거나 직접 입력해 주세요.';
    if (error.status === 429) return '오늘 사진 인식 횟수를 다 썼어요. 직접 입력해 주세요.';
  }
  return getErrorMessage(error);
}
