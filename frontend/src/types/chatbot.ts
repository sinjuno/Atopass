import type { BodyPart, RecordInput } from './record';

export type ChatMode = 'RECORD' | 'QUERY';

export interface ChatSession {
  sessionId: string;
  mode: ChatMode;
}

/** 아직 입력되지 않은 항목 (묻는 순서) */
export type PendingField =
  | 'skinTrend'
  | 'itchScore'
  | 'painIndex'
  | 'bodyParts'
  | 'symptoms'
  | 'skinShape'
  | 'swelling'
  | 'scratch'
  | 'oozing'
  | 'sleepDisturbed'
  | 'sleepHours'
  | 'moisturizer'
  | 'medicationUse'
  | 'notes';

/**
 * 지금 묻는 항목 — 항목 이름(PendingField), 연결 질문 ID(C01~C16),
 * 연결 질문 답을 글로 적는 단계(예: C12_TEXT), 적은 글을 확인하는 단계(예: C12_CONFIRM)
 */
export type AskingField = string;

/** 화면이 따로 그리는 선택지(막대·마네킹)로 답하는 항목. 특이사항은 글로 받고 "없어요"만 선택지로 고른다 */
export type CustomChoiceField = 'painIndex' | 'itchScore' | 'bodyParts' | 'sleepHours' | 'notes';

/** 서버가 정해 주는 선택지 — 고른 선택지 글자를 그대로 답으로 보낸다 */
export interface ChatChoiceSpec {
  /** 항목 이름 (내려 둔 선택지 탭의 "○○ 고르기") */
  label: string;
  options: string[];
  /** 여러 개 고르기. 하나도 안 고르고 완료하면 빈 배열 */
  multiple: boolean;
  /** 여러 개 고르기에서 "하나도 없음" 버튼 글자 (예: 괜찮음) */
  noneLabel: string | null;
  /** 글로 직접 적는 단계 — 입력창에 적은 글이 답이 된다 */
  text: boolean;
}

/** 챗봇이 대화로 모은 기록 초안 (recordDate 제외). skinSigns는 아직 묻지 않았으면 null, "없음"이면 [] */
export type ChatDraft = Omit<RecordInput, 'recordDate'> & { sleepScore?: number | null };

export interface SendMessageResponse {
  /** 3번 연속 해석 실패 시 입력 예시는 서버가 reply에 붙여 준다 */
  reply: string;
  /** false면 해석 실패 — 같은 질문이 다시 온다 (오류 아님, 200) */
  understood: boolean;
  pendingFields: PendingField[];
  /** 지금 묻는 항목 — 이 값에 맞춰 선택지를 보여 준다. symptoms(부위·증상 점수)는 글로 답한다. 확인 단계면 null */
  askingField: AskingField | null;
  /** 지금 묻는 항목의 선택지. 화면이 따로 그리는 항목(고통 지수·가려움 정도·부위·수면 시간·특이사항)이면 null */
  choice: ChatChoiceSpec | null;
  /** true면 물어볼 항목이 끝나 그날 기록이 저장됨 (일일 총정리와 인사가 reply로 온다) */
  requiresConfirmation: boolean;
  /** 지금까지 모은 초안. 질의 모드에서는 null */
  draft: ChatDraft | null;
  /** 그날 기록이 이미 저장되어, 이제 글로 고치면 바로 저장되는 대화 */
  editing: boolean;
  /** 이 대화로 저장한(또는 고치는) 기록. 아직 저장 전이면 null */
  recordId: number | null;
}

/** GET /chatbot/sessions/{sessionId}/draft — 저장된(닫힌) 세션이면 409 SESSION_CLOSED */
export interface ChatDraftState {
  draft: ChatDraft;
  pendingFields: PendingField[];
  askingField: AskingField | null;
  choice: ChatChoiceSpec | null;
  requiresConfirmation: boolean;
  editing: boolean;
  recordId: number | null;
}

/**
 * POST /chatbot/sessions/{sessionId}/choices — 지금 묻는 항목(askingField)에만 답할 수 있다 (아니면 400).
 * LLM을 부르지 않으므로 이용 한도에 세지 않는다
 */
export type ChatChoiceRequest =
  | { field: 'painIndex' | 'itchScore'; value: number }
  /** 불편한 부위. 고른 부위마다 방금 받은 고통 지수가 그 부위의 점수가 된다 */
  | { field: 'bodyParts'; value: BodyPart[] }
  | { field: 'sleepHours'; value: number }
  /** 선택지(choice)로 묻는 항목 — 고른 선택지 글자. 여러 개 고르기는 글자 배열("하나도 없음"이면 []) */
  | { field: AskingField; value: string | string[] }
  /** 넘어가기. 고통 지수 말고는 모두 건너뛸 수 있다. 부위·특이사항은 "없어요" */
  | { field: AskingField; skip: true };

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
