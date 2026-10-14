import type { RecordInput } from './record';

export type ChatMode = 'RECORD' | 'QUERY';

export interface ChatSession {
  sessionId: string;
  mode: ChatMode;
}

/** 아직 입력되지 않은 항목 */
export type PendingField = 'painIndex' | 'symptoms' | 'sleepHours' | 'medication' | 'skinSigns' | 'notes';

/** 챗봇이 대화로 모은 기록 초안 (recordDate 제외). skinSigns는 아직 묻지 않았으면 null, "없음"이면 [] */
export type ChatDraft = Omit<RecordInput, 'recordDate'> & { sleepScore?: number | null };

export interface SendMessageResponse {
  /** 3번 연속 해석 실패 시 입력 예시는 서버가 reply에 붙여 준다 */
  reply: string;
  /** false면 해석 실패 — 같은 질문이 다시 온다 (오류 아님, 200) */
  understood: boolean;
  pendingFields: PendingField[];
  /** true면 일일 총정리(확인) 단계로 이동 */
  requiresConfirmation: boolean;
  /** 지금까지 모은 초안. 질의 모드에서는 null */
  draft: ChatDraft | null;
}

/** GET /chatbot/sessions/{sessionId}/draft — 저장된(닫힌) 세션이면 409 SESSION_CLOSED */
export interface ChatDraftState {
  draft: ChatDraft;
  pendingFields: PendingField[];
  requiresConfirmation: boolean;
}

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
