import type { ApiSuccess } from '@/types/api';
import type {
  ChatChoiceRequest,
  ChatConsent,
  ChatDraftState,
  ChatMessagesResponse,
  ChatMode,
  ChatSession,
  SaveChatRecordRequest,
  SendMessageResponse,
} from '@/types/chatbot';
import type { RecordCreated, SkinSign } from '@/types/record';
import { client, unwrap } from './client';

export const chatbotApi = {
  /** 외부 LLM 이용 동의 여부. 동의 전에는 세션 생성·메시지 전송이 403 CONSENT_REQUIRED */
  getConsent: () => client.get<ApiSuccess<ChatConsent>>('/chatbot/consent').then(unwrap),

  agreeConsent: () => client.post<ApiSuccess<ChatConsent>>('/chatbot/consent').then(unwrap),

  createSession: (mode: ChatMode = 'RECORD') =>
    client.post<ApiSuccess<ChatSession>>('/chatbot/sessions', { mode }).then(unwrap),

  sendMessage: (sessionId: string, message: string) =>
    client
      .post<ApiSuccess<SendMessageResponse>>(`/chatbot/sessions/${sessionId}/messages`, { message })
      .then(unwrap),

  /** 선택지로 고른 답. LLM을 부르지 않는다 */
  sendChoice: (sessionId: string, body: ChatChoiceRequest) =>
    client
      .post<ApiSuccess<SendMessageResponse>>(`/chatbot/sessions/${sessionId}/choices`, body)
      .then(unwrap),

  /** "기록 마치기" — 필수 항목(고통 지수)이 채워졌으면 확인 단계로 넘어간다 */
  finish: (sessionId: string) =>
    client.post<ApiSuccess<SendMessageResponse>>(`/chatbot/sessions/${sessionId}/finish`).then(unwrap),

  getMessages: (sessionId: string) =>
    client.get<ApiSuccess<ChatMessagesResponse>>(`/chatbot/sessions/${sessionId}/messages`).then(unwrap),

  /** 세션의 현재 초안과 진행 상태 (새로고침 뒤 복원용) */
  getDraft: (sessionId: string) =>
    client.get<ApiSuccess<ChatDraftState>>(`/chatbot/sessions/${sessionId}/draft`).then(unwrap),

  /**
   * 증상 예시 사진. 사용자가 보여 달라고 답한 뒤에만 부른다.
   * 인증이 필요해 img src로 직접 못 쓴다 — blob으로 받아 object URL로 표시
   */
  getSkinSignImageUrl: async (sign: SkinSign) => {
    const res = await client.get<Blob>(`/chatbot/skin-signs/${sign}/image`, { responseType: 'blob' });
    return URL.createObjectURL(res.data);
  },

  /** confirmed: true일 때만 저장된다 */
  saveRecord: (sessionId: string, body: SaveChatRecordRequest) =>
    client.post<ApiSuccess<RecordCreated>>(`/chatbot/sessions/${sessionId}/records`, body).then(unwrap),
};
