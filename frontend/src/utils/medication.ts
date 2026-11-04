import { ApiError } from '@/apis/client';
import type { Medication, MedicationInput, MedicationTime, MedicationType } from '@/types/medication';
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

/** 비운 값을 null 로 보내면 백엔드가 "안 바꿈"으로 처리하므로, 이 필드들은 빈 문자열 ''로 보내야 비워진다. (times 는 빈 배열이면 비워진다) */
const CLEARED_WITH_EMPTY_STRING: (keyof MedicationInput)[] = ['dose', 'timesText', 'memo'];

/** 바뀐 필드만 골라 PATCH 본문을 만든다. */
export function changedFields(before: Medication, after: MedicationInput): Partial<MedicationInput> {
  const patch: Partial<MedicationInput> = {};
  (Object.keys(after) as (keyof MedicationInput)[]).forEach((key) => {
    if (JSON.stringify(after[key]) === JSON.stringify(before[key])) return;
    const cleared = after[key] === null && CLEARED_WITH_EMPTY_STRING.includes(key);
    (patch as Record<string, unknown>)[key] = cleared ? '' : after[key];
  });
  return patch;
}
