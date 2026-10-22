// Swagger(openapi.yaml) components.schemas 기준

/** 마네킹 부위(FACE~BACK, 팔·다리는 좌우·상박·하박, 손·발은 좌우, 등은 뒷면)와 예전 부위(CHEST~FOOT) */
export type BodyPart =
  | 'FACE'
  | 'NECK'
  | 'TRUNK'
  | 'RIGHT_UPPER_ARM'
  | 'RIGHT_FOREARM'
  | 'LEFT_UPPER_ARM'
  | 'LEFT_FOREARM'
  | 'GROIN'
  | 'RIGHT_THIGH'
  | 'RIGHT_LOWER_LEG'
  | 'LEFT_THIGH'
  | 'LEFT_LOWER_LEG'
  | 'RIGHT_HAND'
  | 'LEFT_HAND'
  | 'RIGHT_FOOT'
  | 'LEFT_FOOT'
  | 'BACK'
  | 'CHEST'
  | 'ABDOMEN'
  | 'ARM'
  | 'HAND'
  | 'LEG'
  | 'FOOT';

/** 부위 점수 항목 — 지금은 부위의 고통 점수(pain) 하나. 예전 증상별 점수(가려움 등)는 나타난 증상으로 옮겼다 */
export type SymptomKey = 'pain';

// 0~10 정수, 0은 "증상 없음". 기록하지 않은 증상은 키를 생략한다(0으로 채우지 않음)
export type Symptoms = Partial<Record<SymptomKey, number>>;

export type OutdoorActivity = 'LOW' | 'MEDIUM' | 'HIGH';

/** 나타난 증상(있음/없음) — 홍반, 부종/구진(결절), 진물/딱지, 긁은 자국(찰상), 태선화, 건조증, 가려움, 균열, 출혈 */
export type SkinSign =
  | 'ERYTHEMA'
  | 'EDEMA'
  | 'OOZING'
  | 'EXCORIATION'
  | 'LICHENIFICATION'
  | 'XEROSIS'
  | 'ITCHING'
  | 'FISSURE'
  | 'BLEEDING';

/** 오늘 피부 상태 — 좋아짐 / 평소와 비슷 / 평소보다 나빠짐 */
export type SkinTrend = 'BETTER' | 'SAME' | 'WORSE';

/** 처방받은 연고·약 사용 — 사용함 / 안 함 / 처방 없음 */
export type MedicationUse = 'USED' | 'NOT_USED' | 'NO_PRESCRIPTION';

/** 하루 점검 — 챗봇이 묻는 항목 중 위 칸에 없는 것. 기록하지 않은 항목은 null */
export interface DailyCheck {
  skinTrend?: SkinTrend | null;
  /** 가려움 정도 0~10 (0은 없음) */
  itchScore?: number | null;
  /** 가려워서 잠을 설쳤는지 */
  sleepDisturbed?: boolean | null;
  moisturizer?: boolean | null;
  medicationUse?: MedicationUse | null;
  /** 연결 질문 답 — 질문 ID(C01~C16) → 고른 선택지 또는 직접 쓴 글 */
  linkedAnswers?: Record<string, string> | null;
}

export interface SkinCondition {
  bodyPart: BodyPart;
  symptoms: Symptoms;
  /** 이 부위에 나타난 증상. 없으면 생략하거나 빈 배열 */
  skinSigns?: SkinSign[];
}

export interface Lifestyle {
  alcohol?: boolean | null;
  outdoorActivity?: OutdoorActivity | null;
}

/** RecordRequest — POST /records, PUT /records/{id}. skinConditions와 painIndex 중 하나 이상 필요 */
export interface RecordInput {
  recordDate: string; // yyyy-MM-dd
  skinConditions: SkinCondition[];
  /** 고통 지수 0~10 (0은 없음) */
  painIndex?: number | null;
  sleepHours?: number | null;
  /** 약 복용 여부 */
  medication?: boolean | null;
  skinSigns?: SkinSign[] | null;
  lifestyle?: Lifestyle | null;
  notes?: string | null;
  /** 하루 점검. 보내지 않으면(null) 수정할 때 저장된 값을 그대로 둔다 */
  dailyCheck?: DailyCheck | null;
}

/** PhotoRef — 기록 상세/비교 응답의 사진 목록 항목 */
export interface PhotoRef {
  photoId: number;
  uploadedAt?: string;
}

/** PhotoUploaded — POST /records/{recordId}/photos 응답 */
export interface PhotoUploaded {
  photoId: number;
  recordId: number;
  uploadedAt: string;
}

/** RecordDetail — GET /records/{recordId}, PUT 응답 */
export interface RecordDetail extends RecordInput {
  recordId: number;
  /** 수면 점수 1~5 (서버 계산, 수면 시간이 없으면 없음) */
  sleepScore?: number | null;
  photos: PhotoRef[];
  createdAt?: string;
  updatedAt?: string;
}

export interface RecordCreated {
  recordId: number;
  recordDate: string;
  createdAt: string;
}

/** RecordSummary — 목록 항목. 포함할 점수 필드는 협의 후 확정 */
export interface RecordSummary {
  recordId: number;
  recordDate: string;
  skinConditions?: SkinCondition[];
  painIndex?: number | null;
  sleepHours?: number | null;
  sleepScore?: number | null;
  photoCount?: number;
}

/** RecordPage — GET /records 응답 */
export interface RecordPage {
  content: RecordSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface RecordListParams {
  startDate?: string;
  endDate?: string;
  page?: number;
  size?: number;
}
