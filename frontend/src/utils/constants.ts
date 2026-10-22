import type { CustomChoiceField, PendingField } from '@/types/chatbot';
import type { BodyPart, MedicationUse, OutdoorActivity, SkinSign, SkinTrend, SymptomKey } from '@/types/record';

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

/** 나타난 증상 7가지 — 이 순서로 보여 준다 (1 홍반 … 7 가려움). 서버 SkinSign 선언 순서와 같다 */
export const SKIN_SIGN_LABELS: Record<SkinSign, string> = {
  ERYTHEMA: '홍반',
  EDEMA: '부종/구진(결절)',
  OOZING: '진물/딱지',
  EXCORIATION: '긁은 자국(찰상)',
  LICHENIFICATION: '태선화',
  XEROSIS: '건조증',
  ITCHING: '가려움',
  FISSURE: '균열',
  BLEEDING: '출혈',
};

/** 증상을 쉬운 말로 풀어 쓴 설명. 증상을 고르는 버튼 아래에 작게 보여 준다 (서버 SkinSign의 설명과 같다) */
export const SKIN_SIGN_DESCRIPTIONS: Record<SkinSign, string> = {
  ERYTHEMA: '피부가 붉어짐',
  EDEMA: '피부가 붓거나 오돌토돌 솟아오름, 단단한 멍울',
  OOZING: '상처에서 진물이 나오거나 딱지가 앉음',
  EXCORIATION: '긁어서 피부가 까지거나 긁힌 자국이 남음',
  LICHENIFICATION: '오래 긁어서 피부가 두꺼워지고 거칠어짐, 주름이 깊어짐',
  XEROSIS: '피부가 건조하고 당기거나 하얗게 일어남',
  ITCHING: '피부가 가려움',
  FISSURE: '피부가 갈라지거나 틈이 생김',
  BLEEDING: '긁거나 갈라진 곳에서 피가 남',
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

/** 챗봇이 묻는 항목 이름 (예: 내려 둔 선택지 탭의 "수면 시간 고르기") */
export const PENDING_FIELD_LABELS: Record<PendingField, string> = {
  skinTrend: '피부 상태',
  itchScore: '가려움 정도',
  painIndex: '고통 지수',
  bodyParts: '불편한 부위',
  symptoms: '부위별 점수',
  skinShape: '피부 모양',
  swelling: '붓기',
  scratch: '긁은 자국',
  oozing: '진물·피',
  sleepDisturbed: '수면 방해',
  sleepHours: '수면 시간',
  moisturizer: '보습제',
  medicationUse: '연고·약',
  notes: '특이사항',
};

/** 챗봇 선택지 중 화면이 따로 그리는 것(막대·마네킹). 그 밖의 항목은 서버가 준 선택지(choice)로 그린다 */
export const CUSTOM_CHOICE_FIELDS: readonly CustomChoiceField[] = ['painIndex', 'itchScore', 'bodyParts', 'sleepHours', 'notes'];

export const SKIN_TREND_LABELS: Record<SkinTrend, string> = {
  BETTER: '좋아짐',
  SAME: '평소와 비슷',
  WORSE: '평소보다 나빠짐',
};

export const MEDICATION_USE_LABELS: Record<MedicationUse, string> = {
  USED: '사용함',
  NOT_USED: '안 함',
  NO_PRESCRIPTION: '처방 없음',
};

/** 연결 질문 답을 기록에 보여 줄 이름 (질문 ID → 이름) */
export const LINKED_ANSWER_LABELS: Record<string, string> = {
  C01: '불편 시간대',
  C02: '악화 순간',
  C03: '접촉 요인',
  C04: '새 제품',
  C05: '손 씻기 빈도',
  C06: '보습 시점',
  C07: '붓기·열감',
  C08: '긁는 상황',
  C09: '발견 시점',
  C10: '취침 시간 변화',
  C11: '땀 관리',
  C12: '음식 메모',
  C13: '마스크 착용',
  C14: '실내 난방',
  C15: '옷 소재',
  C16: '에어컨 사용',
};

/** 챗봇 이용 동의 팝업 문구 */
export const CHAT_CONSENT_TEXT = [
  '챗봇과 나눈 대화와 기록 내용이 답변을 만들기 위해 외부 AI(Claude)로 전달돼요.',
  '이메일 같은 식별정보는 전달되지 않아요.',
];

/** 미기록 값 표시 문구 — null을 0이나 기본값으로 채우지 않는다 */
export const EMPTY_TEXT = '기록 없음';

export const DISCLAIMER_TEXT = '의료적 진단이나 판단을 대체하지 않아요';
