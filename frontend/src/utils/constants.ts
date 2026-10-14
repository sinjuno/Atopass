import type { PendingField } from '@/types/chatbot';
import type { BodyPart, OutdoorActivity, SkinSign, SymptomKey } from '@/types/record';

// Swagger BodyPart enum 순서
export const BODY_PART_LABELS: Record<BodyPart, string> = {
  FACE: '얼굴',
  NECK: '목',
  CHEST: '가슴',
  BACK: '등',
  ABDOMEN: '배',
  ARM: '팔',
  HAND: '손',
  LEG: '다리',
  FOOT: '발',
};

export const SYMPTOM_LABELS: Record<SymptomKey, string> = {
  itching: '가려움',
  stinging: '따가움',
  redness: '붉은기',
  dryness: '건조함',
  flaking: '각질',
  swelling: '부어오름',
  pain: '통증',
};

export const OUTDOOR_ACTIVITY_LABELS: Record<OutdoorActivity, string> = {
  LOW: '적음',
  MEDIUM: '보통',
  HIGH: '많음',
};

export const SCORE_MIN = 0;
export const SCORE_MAX = 10;

/** 고통 지수 1~10 */
export const PAIN_INDEX_MIN = 1;
export const PAIN_INDEX_MAX = 10;

/** 챗봇 질문 순서와 같은 번호 순서 (1 피부건조증 … 7 태선화) */
export const SKIN_SIGN_LABELS: Record<SkinSign, string> = {
  XEROSIS: '피부건조증',
  ERYTHEMA: '홍반',
  SCALING: '각질',
  PAPULE: '구진',
  EXCORIATION: '긁은 자국',
  NODULE: '결절',
  LICHENIFICATION: '태선화',
};

/** 수면 점수: 7~9시간이 5점, 2시간 벗어날 때마다 1점 감점 (최저 1점) */
export const SLEEP_SCORE_MAX = 5;

// 사진 제한 (API 명세서: 피부 사진 업로드)
export const PHOTO_MAX_COUNT = 5;
export const PHOTO_MAX_BYTES = 10 * 1024 * 1024;
export const PHOTO_ACCEPT = ['image/jpeg', 'image/png'];

export const CHAT_MESSAGE_MAX_LENGTH = 500;

/** 특이사항 최대 길이 — 챗봇으로 기록하면 채팅 한 번(500자), 직접 입력은 2000자 */
export const CHAT_NOTES_MAX_LENGTH = 500;
export const NOTES_MAX_LENGTH = 2000;

/** 챗봇 응답 pendingFields → "남은 항목" 표시 문구 */
export const PENDING_FIELD_LABELS: Record<PendingField, string> = {
  painIndex: '고통 지수',
  symptoms: '부위별 점수',
  sleepHours: '수면 시간',
  medication: '약 복용',
  skinSigns: '나타난 증상',
  notes: '특이사항',
};

/** 챗봇 이용 동의 팝업 문구 */
export const CHAT_CONSENT_TEXT = [
  '챗봇과 나눈 대화와 기록 내용이 답변을 만들기 위해 외부 AI(Claude)로 전달돼요.',
  '이메일 같은 식별정보는 전달되지 않아요.',
];

/** 미기록 값 표시 문구 — null을 0이나 기본값으로 채우지 않는다 */
export const EMPTY_TEXT = '기록 없음';

export const DISCLAIMER_TEXT = '의료적 진단이나 판단을 대체하지 않아요';
