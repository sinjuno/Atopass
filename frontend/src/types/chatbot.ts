import type { BodyPart, RecordInput, SkinSign } from './record';

export type ChatMode = 'RECORD' | 'QUERY';

export interface ChatSession {
  sessionId: string;
  mode: ChatMode;
}

/** 아직 입력되지 않은 항목 */
export type PendingField = 'painIndex' | 'bodyParts' | 'symptoms' | 'sleepHours' | 'medication' | 'skinSigns' | 'notes';

/** 선택지로 답하는 항목. 특이사항은 글로 받고 "없어요"만 선택지로 고른다 */
export type ChoiceField = Exclude<PendingField, 'symptoms'>;

/** 챗봇이 대화로 모은 기록 초안 (recordDate 제외). skinSigns는 아직 묻지 않았으면 null, "없음"이면 [] */
export type ChatDraft = Omit<RecordInput, 'recordDate'> & { sleepScore?: number | null };

export interface SendMessageResponse {
  /** 3번 연속 해석 실패 시 입력 예시는 서버가 reply에 붙여 준다 */
  reply: string;
  /** false면 해석 실패 — 같은 질문이 다시 온다 (오류 아님, 200) */
  understood: boolean;
  pendingFields: PendingField[];
  /** 지금 묻는 항목 — 이 값에 맞춰 선택지를 보여 준다. symptoms(부위·증상 점수)는 글로 답한다. 확인 단계면 null */
  askingField: PendingField | null;
  /** true면 일일 총정리(확인) 단계로 이동 */
  requiresConfirmation: boolean;
  /** 지금까지 모은 초안. 질의 모드에서는 null */
  draft: ChatDraft | null;
  /** 이미 저장된 그날 기록을 고치는 대화. 확인 단계에서 총정리로 가지 않고 챗봇 화면에서 바로 저장한다 */
  editing: boolean;
}

/** GET /chatbot/sessions/{sessionId}/draft — 저장된(닫힌) 세션이면 409 SESSION_CLOSED */
export interface ChatDraftState {
  draft: ChatDraft;
  pendingFields: PendingField[];
  askingField: PendingField | null;
  requiresConfirmation: boolean;
  editing: boolean;
}

/**
 * POST /chatbot/sessions/{sessionId}/choices — 지금 묻는 항목(askingField)에만 답할 수 있다 (아니면 400).
 * LLM을 부르지 않으므로 이용 한도에 세지 않는다
 */
export type ChatChoiceRequest =
  | { field: 'painIndex'; value: number }
  /** 고통이 있는 부위. 고른 부위마다 방금 받은 고통 지수가 그 부위의 점수가 된다 */
  | { field: 'bodyParts'; value: BodyPart[] }
  | { field: 'sleepHours'; value: number }
  | { field: 'medication'; value: boolean }
  /** 빈 배열이면 "증상 없음" */
  | { field: 'skinSigns'; value: SkinSign[] }
  /** 넘어가기. 부위·특이사항은 "없어요" */
  | { field: Exclude<ChoiceField, 'painIndex'>; skip: true };

/** GET·POST /chatbot/consent */
export interface ChatConsent {
  agreed: boolean;
  agreedAt: string | null;
}

export type ChatRole = 'USER' | 'ASSISTANT';

export interface ChatMessage {
  role: ChatRole;
  content: string;
  createdAt: string;
}

export interface ChatMessagesResponse {
  messages: ChatMessage[];
}

/** POST /chatbot/sessions/{sessionId}/records */
export interface SaveChatRecordRequest {
  recordDate: string;
  confirmed: boolean;
  record: Omit<RecordInput, 'recordDate'>;
}
