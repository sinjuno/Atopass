// Swagger(openapi.yaml) components.schemas 기준

export type BodyPart = 'FACE' | 'NECK' | 'CHEST' | 'BACK' | 'ABDOMEN' | 'ARM' | 'HAND' | 'LEG' | 'FOOT';

export type SymptomKey = 'itching' | 'stinging' | 'redness' | 'dryness' | 'flaking' | 'swelling' | 'pain';

// 0~10 정수, 0은 "증상 없음". 기록하지 않은 증상은 키를 생략한다(0으로 채우지 않음)
export type Symptoms = Partial<Record<SymptomKey, number>>;

export type OutdoorActivity = 'LOW' | 'MEDIUM' | 'HIGH';

/** 나타난 증상(있음/없음) — 피부건조증, 홍반, 각질, 구진, 긁은 자국, 결절, 태선화 */
export type SkinSign =
  | 'XEROSIS'
  | 'ERYTHEMA'
  | 'SCALING'
  | 'PAPULE'
  | 'EXCORIATION'
  | 'NODULE'
  | 'LICHENIFICATION';

export interface SkinCondition {
  bodyPart: BodyPart;
  symptoms: Symptoms;
}

export interface Lifestyle {
  alcohol?: boolean | null;
  outdoorActivity?: OutdoorActivity | null;
}

/** RecordRequest — POST /records, PUT /records/{id}. skinConditions와 painIndex 중 하나 이상 필요 */
export interface RecordInput {
  recordDate: string; // yyyy-MM-dd
  skinConditions: SkinCondition[];
  /** 고통 지수 1~10 */
  painIndex?: number | null;
  sleepHours?: number | null;
  /** 약 복용 여부 */
  medication?: boolean | null;
  skinSigns?: SkinSign[] | null;
  lifestyle?: Lifestyle | null;
  notes?: string | null;
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
