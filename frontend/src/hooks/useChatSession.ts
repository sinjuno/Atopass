import { useCallback, useEffect, useRef, useState } from 'react';
import { chatbotApi } from '@/apis/chatbot';
import { ApiError } from '@/apis/client';
import type { ChatMessage, PendingField } from '@/types/chatbot';
import { chatSessionStore } from './useRecordDraft';

/**
 * - consent: 외부 LLM 이용 동의가 필요함 (동의 팝업 표시)
 * - closed: 이 날짜의 대화는 이미 기록으로 저장됨 (총정리로 이동)
 */
export type ChatStatus = 'loading' | 'consent' | 'ready' | 'sending' | 'error' | 'closed';

interface SendResult {
  ok: boolean;
  /** SESSION_CLOSED — 이미 저장된 기록이라 총정리로 이동해야 함 */
  closed?: boolean;
  /** CHAT_LIMIT_EXCEEDED — 최근 24시간 이용 한도 도달, 총정리에서 직접 입력하도록 안내 */
  limitReached?: boolean;
}

const isApiError = (e: unknown, code: string) => e instanceof ApiError && e.code === code;

/**
 * 기록(RECORD) 모드 챗봇 세션.
 * - 들어올 때 이용 동의부터 확인하고, 동의 전에는 세션을 만들지 않는다
 * - 날짜별 sessionId가 있으면 대화 내역과 진행 상태(초안 조회 API)를 복원, 없으면 새 세션 생성
 * - 응답의 pendingFields / requiresConfirmation 상태를 노출
 */
export function useChatSession(recordDate: string) {
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [pendingFields, setPendingFields] = useState<PendingField[]>([]);
  const [requiresConfirmation, setRequiresConfirmation] = useState(false);
  const [status, setStatus] = useState<ChatStatus>('loading');
  const [error, setError] = useState<string | null>(null);
  // 한도에 도달해도 대화 내역·초안 조회는 되므로 status는 그대로 두고 안내만 띄운다
  const [limitReached, setLimitReached] = useState(false);
  const startedRef = useRef(false);

  const start = useCallback(async () => {
    setStatus('loading');
    setError(null);
    try {
      const consent = await chatbotApi.getConsent();
      if (!consent.agreed) {
        setStatus('consent');
        return;
      }

      const saved = chatSessionStore.get(recordDate);
      if (saved) {
        try {
          const [{ messages }, state] = await Promise.all([
            chatbotApi.getMessages(saved),
            chatbotApi.getDraft(saved),
          ]);
          setSessionId(saved);
          setMessages(messages);
          setPendingFields(state.pendingFields);
          setRequiresConfirmation(state.requiresConfirmation);
          setStatus('ready');
          return;
        } catch (e) {
          if (isApiError(e, 'SESSION_CLOSED')) {
            // 이미 기록으로 저장된 대화 — 저장된 기록은 총정리 화면에서 본다
            chatSessionStore.clear(recordDate);
            setStatus('closed');
            return;
          }
          // 세션이 만료(30일 보존)되었거나 없으면 새로 만든다
          if (!isApiError(e, 'SESSION_NOT_FOUND')) throw e;
          chatSessionStore.clear(recordDate);
        }
      }
      const session = await chatbotApi.createSession('RECORD');
      chatSessionStore.set(recordDate, session.sessionId);
      setSessionId(session.sessionId);
      setMessages([]);
      setPendingFields([]);
      setRequiresConfirmation(false);
      setStatus('ready');
    } catch (e) {
      if (isApiError(e, 'CONSENT_REQUIRED')) {
        setStatus('consent');
        return;
      }
      setError(e instanceof Error ? e.message : '대화를 시작하지 못했어요.');
      setStatus('error');
    }
  }, [recordDate]);

  useEffect(() => {
    // StrictMode 개발 환경에서 세션이 두 번 생성되지 않도록
    if (startedRef.current) return;
    startedRef.current = true;
    void start();
  }, [start]);

  /** 동의 팝업에서 동의하면 서버에 저장하고 대화를 시작한다 */
  const agree = useCallback(async () => {
    try {
      await chatbotApi.agreeConsent();
    } catch (e) {
      setError(e instanceof Error ? e.message : '동의를 저장하지 못했어요.');
      return;
    }
    await start();
  }, [start]);

  const send = useCallback(
    async (text: string): Promise<SendResult> => {
      if (!sessionId) return { ok: false };
      const userMessage: ChatMessage = { role: 'USER', content: text, createdAt: new Date().toISOString() };
      setMessages((prev) => [...prev, userMessage]);
      setStatus('sending');
      setError(null);
      try {
        const res = await chatbotApi.sendMessage(sessionId, text);
        // understood: false여도 reply에 같은 질문(필요하면 입력 예시 포함)이 오므로 그대로 표시한다
        setMessages((prev) => [
          ...prev,
          { role: 'ASSISTANT', content: res.reply, createdAt: new Date().toISOString() },
        ]);
        setPendingFields(res.pendingFields);
        setRequiresConfirmation(res.requiresConfirmation);
        setStatus('ready');
        setLimitReached(false);
        return { ok: true };
      } catch (e) {
        // 실패한 메시지는 목록에서 빼고, 입력창에 내용을 되돌려 놓도록 호출부에 알린다
        setMessages((prev) => prev.filter((m) => m !== userMessage));
        if (isApiError(e, 'CONSENT_REQUIRED')) {
          // 다른 기기에서 동의를 철회한 경우 등 — 팝업을 다시 띄운다
          setStatus('consent');
          return { ok: false };
        }
        setStatus('ready');
        if (isApiError(e, 'SESSION_CLOSED')) {
          chatSessionStore.clear(recordDate);
          setError('이미 저장된 기록이에요. 총정리 화면으로 이동할게요.');
          return { ok: false, closed: true };
        }
        if (isApiError(e, 'CHAT_LIMIT_EXCEEDED')) {
          setLimitReached(true);
          return { ok: false, limitReached: true };
        }
        if (isApiError(e, 'CHATBOT_UNAVAILABLE')) {
          setError('잠시 후 다시 시도해 주세요.');
        } else {
          setError(e instanceof Error ? e.message : '메시지를 보내지 못했어요.');
        }
        return { ok: false };
      }
    },
    [sessionId, recordDate],
  );

  return {
    sessionId,
    messages,
    pendingFields,
    requiresConfirmation,
    status,
    error,
    limitReached,
    send,
    agree,
    retry: start,
  };
}
