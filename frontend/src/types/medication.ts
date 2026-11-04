// 복용 약 등록·수정 API 약속 (공통)
export type MedicationType = 'oral' | 'topical' | 'other';
export type MedicationTime = 'morning' | 'lunch' | 'evening' | 'bedtime';
export type MedicationSource = 'signup' | 'mypage' | 'photo' | 'revisit';

export interface Medication {
  id: string;
  name: string;
  type: MedicationType;
  dose: string | null;
  times: MedicationTime[];
  /** 사진 원문 "1일 2회 식후 30분" */
  timesText: string | null;
  startDate: string;
  /** 값이 있으면 복용 중단 */
  endDate: string | null;
  memo: string | null;
  isOtherCondition: boolean;
  source: MedicationSource;
}

/** 저장 요청 본문(id 없음). 폼이 만드는 값이기도 하다. */
export type MedicationInput = Omit<Medication, 'id' | 'source'>;

/** 사진 확인 화면용 — 사진에서 못 읽은 name·type 은 null 로 온다(추측 금지). */
export interface ExtractedMedication extends Omit<MedicationInput, 'name' | 'type'> {
  name: string | null;
  type: MedicationType | null;
}

export type MedicationStatus = 'active' | 'stopped';

export interface MedicationHistoryItem {
  changedAt: string;
  field: string;
  before: string | null;
  after: string | null;
}

export type OnboardingStep = 'pending' | 'done' | 'skipped';
