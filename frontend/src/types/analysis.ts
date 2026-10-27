import type { BodyPart, Lifestyle, PhotoRef, SkinCondition, SkinSign, SymptomKey } from './record';

export interface TrendParams {
  startDate: string;
  endDate: string;
  bodyPart: BodyPart;
  symptom: SymptomKey;
}

export interface TrendPoint {
  date: string;
  score: number;
  recordId: number;
}

export interface TrendResponse {
  points: TrendPoint[];
}

/** SYMPTOM_CHANGE(부위·증상 점수 변화) 또는 PAIN_INDEX_CHANGE(불편함 지수 변화) */
export type InsightType = 'SYMPTOM_CHANGE' | 'PAIN_INDEX_CHANGE';

export interface Insight {
  type: InsightType;
  message: string;
  /** 불편함 지수 변화(PAIN_INDEX_CHANGE)이면 null */
  bodyPart: BodyPart | null;
  /** 불편함 지수 변화(PAIN_INDEX_CHANGE)이면 null */
  symptom: SymptomKey | null;
  /** 평균보다 높게(HIGH) 또는 낮게(LOW) 기록된 구간 */
  direction?: 'HIGH' | 'LOW';
  startDate?: string;
  endDate?: string;
}

export interface AnalysisSummary {
  period: { startDate: string; endDate: string };
  recordCount: number;
  availableForComparison: boolean;
  insights: Insight[];
}

export interface ComparisonDay {
  date: string;
  recordId: number;
  skinConditions: SkinCondition[];
  /** 불편함 지수 0~10 (0도 기록한 값). 기록하지 않았으면 null */
  painIndex: number | null;
  sleepHours: number | null;
  /** 수면 점수 1~5. 수면 시간이 없으면 null */
  sleepScore: number | null;
  /** 약 복용 여부. 기록하지 않았으면 null */
  medication: boolean | null;
  /** 나타난 증상. 없으면 빈 배열 */
  skinSigns: SkinSign[];
  lifestyle: Lifestyle | null;
  notes: string | null;
  photos: PhotoRef[];
}

export interface ComparisonResponse {
  A: ComparisonDay | null;
  B: ComparisonDay | null;
}
