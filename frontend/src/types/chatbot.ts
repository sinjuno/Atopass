import type { BodyPart, RecordInput, SkinSign } from './record';

export type ChatMode = 'RECORD' | 'QUERY';

export interface ChatSession {
  sessionId: string;
  mode: ChatMode;
}

/**
 * 아직 입력되지 않은 항목. signImage는 기록 항목이 아니라, 사용자가 증상을 물었을 때
 * 예시 사진을 보여 줘도 되는지 묻는 중이라는 뜻이다 (askingField로만 온다)
 */
export type PendingField =
  | 'painIndex'
  | 'bodyParts'
  | 'symptoms'
  | 'sleepHours'
  | 'medication'
  | 'skinSigns'
  | 'notes'
  | 'signImage'
  /** 상황 질문. 조건에 맞을 때만, 수면 시간 다음~특이사항 앞에 하루 1개 나온다 */
  | 'P01'
  | 'P02'
  | 'P03'
  | 'P04'
  | 'P05'
  | 'P06'
  | 'P07'
  /** 마무리 질문. 매일, 특이사항 다음에 나온다 */
  | 'C02';

export type ShortSleepReason = 'ITCH_WOKE' | 'LATE_SLEEP' | 'BOTH' | 'OTHER';
export type MissedMedicationReason = 'FORGOT' | 'RAN_OUT' | 'AWAY' | 'OTHER';
export type LongGapAnswer = 'SIMILAR' | 'BETTER' | 'WORSE';

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
  /** true면 물어볼 항목이 끝나 그날 기록이 저장됨 (일일 총정리와 인사가 reply로 온다) */
  requiresConfirmation: boolean;
  /** 지금까지 모은 초안. 질의 모드에서는 null */
  draft: ChatDraft | null;
  /** 그날 기록이 이미 저장되어, 이제 글로 고치면 바로 저장되는 대화 */
  editing: boolean;
  /** 이 대화로 저장한(또는 고치는) 기록. 아직 저장 전이면 null */
  recordId: number | null;
  /** 이 답과 함께 보여 줄 증상 예시 사진. 사용자가 보여 달라고 답했을 때만 온다 */
  imageSign: SkinSign | null;
  /** 글 입력칸 예시. 특이사항을 묻는 중일 때만 오고, 질문 문구와 같은 조건(재진일·고통 지수)으로 서버가 고른다 */
  inputPlaceholder?: string | null;
}

/** GET /chatbot/sessions/{sessionId}/draft — 저장된(닫힌) 세션이면 409 SESSION_CLOSED */
export interface ChatDraftState {
  draft: ChatDraft;
  pendingFields: PendingField[];
  askingField: PendingField | null;
  requiresConfirmation: boolean;
  editing: boolean;
  recordId: number | null;
  /** 글 입력칸 예시. 특이사항을 묻는 중일 때만 온다 */
  inputPlaceholder?: string | null;
}

/**
 * POST /chatbot/sessions/{sessionId}/choices — 지금 묻는 항목(askingField)에만 답할 수 있다 (아니면 400).
 * LLM을 부르지 않으므로 이용 한도에 세지 않는다
 */
export type ChatChoiceRequest =
  | { field: 'painIndex'; value: number }
  /** 증상이 나타난(고통이 있는) 부위. 고른 부위마다 고통 지수가 그 부위의 점수가, 방금 고른 증상이 그 부위의 증상이 된다 */
  | { field: 'bodyParts'; value: BodyPart[] }
  | { field: 'sleepHours'; value: number }
  | { field: 'medication'; value: boolean }
  /** 빈 배열이면 "증상 없음" */
  | { field: 'skinSigns'; value: SkinSign[] }
  /** 증상 예시 사진을 보여 줄지 */
  | { field: 'signImage'; value: boolean }
  /** 상황 질문(P01) 답 — 고통 지수가 언제부터 심해졌는지 */
  | { field: 'P01'; value: 'TODAY' | 'SINCE_YESTERDAY' | 'FEW_DAYS_AGO' }
  /** 상황 질문(P02) 답 — 어제 없던 증상·부위가 어떻게 시작됐는지 (글, 200자 이내) */
  | { field: 'P02'; value: string }
  /** 상황 질문(P04) 답 — 잠을 적게 잔 이유 */
  | { field: 'P04'; value: ShortSleepReason }
  /** 상황 질문(P05) 답 — 약을 못 챙긴 이유 */
  | { field: 'P05'; value: MissedMedicationReason }
  /** 상황 질문(P03) 답 — 며칠째 많이 불편한 동안 생활에서 달라진 것 (글, 200자 이내) */
  | { field: 'P03'; value: string }
  /** 상황 질문(P06) 답 — 며칠 만에 기록할 때 그동안 피부가 어땠는지, 덧붙인 글은 선택 (200자 이내) */
  | { field: 'P06'; value: { answer: LongGapAnswer; text?: string } }
  /** 상황 질문(P07) 답 — 많이 좋아진 날 달라진 것 (글, 200자 이내) */
  | { field: 'P07'; value: string }
  /** 마무리 질문(C02) 답 — 체크한 항목만 보낸다 */
  | { field: 'C02'; value: { productNote?: string; unusualNote?: string; clinicQuestion?: string } }
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
  /** 챗봇이 이 말과 함께 보여 준 증상 예시 사진 */
  imageSign?: SkinSign | null;
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
