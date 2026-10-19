import type { PendingField } from '@/types/chatbot';
import type { BodyPart, OutdoorActivity, SkinSign, SymptomKey } from '@/types/record';

// Swagger BodyPart enum 순서
export const BODY_PART_LABELS: Record<BodyPart, string> = {
  FACE: '얼굴',
  NECK: '목',
  TRUNK: '몸',
  RIGHT_UPPER_ARM: '오른팔 상박',
  RIGHT_FOREARM: '오른팔 하박',
  LEFT_UPPER_ARM: '왼팔 상박',
  LEFT_FOREARM: '왼팔 하박',
  GROIN: '사타구니',
  RIGHT_THIGH: '오른다리 상박',
  RIGHT_LOWER_LEG: '오른다리 하박',
  LEFT_THIGH: '왼다리 상박',
  LEFT_LOWER_LEG: '왼다리 하박',
  RIGHT_HAND: '오른손',
  LEFT_HAND: '왼손',
  RIGHT_FOOT: '오른발',
  LEFT_FOOT: '왼발',
  BACK: '등',
  // 예전 부위 — 팔·다리를 나누기 전 기록을 보여 줄 때만 쓴다
  CHEST: '가슴',
  ABDOMEN: '배',
  ARM: '팔',
  HAND: '손',
  LEG: '다리',
  FOOT: '발',
};

/** 마네킹에서 고를 수 있는 부위 (새 기록은 이 부위로만 남긴다) */
export const BODY_MAP_PARTS: BodyPart[] = [
  'FACE',
  'NECK',
  'TRUNK',
  'RIGHT_UPPER_ARM',
  'RIGHT_FOREARM',
  'LEFT_UPPER_ARM',
  'LEFT_FOREARM',
  'GROIN',
  'RIGHT_THIGH',
  'RIGHT_LOWER_LEG',
  'LEFT_THIGH',
  'LEFT_LOWER_LEG',
  'RIGHT_HAND',
  'LEFT_HAND',
  'RIGHT_FOOT',
  'LEFT_FOOT',
  'BACK',
];

/** 부위별 고통 점수는 증상 하나(pain)로 저장한다 — 증상별 점수를 하나로 합친 값 */
export const PART_PAIN_SYMPTOM: SymptomKey = 'pain';

export const SYMPTOM_LABELS: Record<SymptomKey, string> = {
  pain: '고통',
};

export const OUTDOOR_ACTIVITY_LABELS: Record<OutdoorActivity, string> = {
  LOW: '적음',
  MEDIUM: '보통',
  HIGH: '많음',
};

export const SCORE_MIN = 0;
export const SCORE_MAX = 10;

/** 기온(°C) 그래프 세로축. 국내 기후 기준 -10~40도면 연중 대부분을 보여 줄 수 있다. */
export const TEMPERATURE_MIN = -10;
export const TEMPERATURE_MAX = 40;
export const TEMPERATURE_TICKS = [-10, 0, 10, 20, 30, 40];

/** 습도(%) 그래프 세로축 */
export const HUMIDITY_MIN = 0;
export const HUMIDITY_MAX = 100;
export const HUMIDITY_TICKS = [0, 20, 40, 60, 80, 100];

/** 고통 지수 0~10 (0은 없음) */
export const PAIN_INDEX_MIN = 0;
export const PAIN_INDEX_MAX = 10;

/**
 * 겹쳐보기 그래프의 지표별 고정 색. 순서가 CVD 안전성 근거이므로 항상 같은 순서(슬롯)를 쓴다 —
 * 어떤 지표를 켜고 끄든 같은 지표는 항상 같은 색을 유지한다(선택 순서로 재배정하지 않음).
 */
export const OVERLAY_SERIES_COLORS = {
  score: '#2a78d6',
  sleep: '#eb6834',
  temperature: '#1baf7a',
  humidity: '#eda100',
} as const;

/** 나타난 증상 7가지 — 이 순서로 보여 준다 (1 홍반 … 7 가려움). 서버 SkinSign 선언 순서와 같다 */
export const SKIN_SIGN_LABELS: Record<SkinSign, string> = {
  ERYTHEMA: '홍반',
  EDEMA: '부종/구진(결절)',
  OOZING: '진물/딱지',
  EXCORIATION: '긁은 자국(찰상)',
  LICHENIFICATION: '태선화',
  XEROSIS: '건조증',
  ITCHING: '가려움',
};

/** 수면 점수: 7~8시간이 5점, 2시간 벗어날 때마다 1점 감점 (최저 1점) */
export const SLEEP_SCORE_MAX = 5;

// 사진 제한 (API 명세서: 피부 사진 업로드)
export const PHOTO_MAX_COUNT = 5;
export const PHOTO_MAX_BYTES = 10 * 1024 * 1024;
export const PHOTO_ACCEPT = ['image/jpeg', 'image/png'];

export const CHAT_MESSAGE_MAX_LENGTH = 500;

/** 특이사항 최대 길이 — 챗봇으로 기록하면 채팅 한 번(500자), 직접 입력은 2000자 */
export const CHAT_NOTES_MAX_LENGTH = 500;
export const NOTES_MAX_LENGTH = 2000;

/** 챗봇 수면 시간 선택지 — 0~12시간, 1시간 단위 */
export const CHAT_SLEEP_HOURS_OPTIONS = Array.from({ length: 13 }, (_, i) => i);
export const CHAT_SLEEP_HOURS_DEFAULT = 7;

/** 챗봇 응답 pendingFields → "남은 항목" 표시 문구 */
export const PENDING_FIELD_LABELS: Record<PendingField, string> = {
  painIndex: '고통 지수',
  bodyParts: '고통 부위',
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
