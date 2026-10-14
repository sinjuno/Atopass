import { useCallback, useEffect, useRef, useState } from 'react';
import { chatbotApi } from '@/apis/chatbot';
import { ApiError } from '@/apis/client';
import type { ChatMessage } from '@/types/chatbot';
import { chatSessionStore } from './useRecordDraft';

export type ChatStatus = 'loading' | 'ready' | 'sending' | 'error';

interface SendResult {
  ok: boolean;
  /** SESSION_CLOSED — 이미 저장된 기록이라 총정리로 이동해야 함 */
  closed?: boolean;
}

/**
 * 기록(RECORD) 모드 챗봇 세션.
 * - 날짜별 sessionId가 있으면 대화 내역을 복원, 없으면 새 세션 생성
 * - 응답의 pendingFields / requiresConfirmation 상태를 노출
 */
export function useChatSession(recordDate: string) {
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [pendingFields, setPendingFields] = useState<string[]>([]);
  const [requiresConfirmation, setRequiresConfirmation] = useState(false);
  const [status, setStatus] = useState<ChatStatus>('loading');
  const [error, setError] = useState<string | null>(null);
  const startedRef = useRef(false);

  const start = useCallback(async () => {
    setStatus('loading');
    setError(null);
    try {
      const saved = chatSessionStore.get(recordDate);
      if (saved) {
        try {
          const { messages } = await chatbotApi.getMessages(saved);
          setSessionId(saved);
          setMessages(messages);
          setStatus('ready');
          return;
        } catch (e) {
          // 세션이 만료(30일 보존)되었거나 없으면 새로 만든다
          if (!(e instanceof ApiError && e.code === 'SESSION_NOT_FOUND')) throw e;
          chatSessionStore.clear(recordDate);
        }
      }
      const session = await chatbotApi.createSession('RECORD');
      chatSessionStore.set(recordDate, session.sessionId);
      setSessionId(session.sessionId);
      setMessages([]);
      setStatus('ready');
    } catch (e) {
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

  const send = useCallback(
    async (text: string): Promise<SendResult> => {
      if (!sessionId) return { ok: false };
      const userMessage: ChatMessage = { role: 'USER', content: text, createdAt: new Date().toISOString() };
      setMessages((prev) => [...prev, userMessage]);
      setStatus('sending');
      setError(null);
      try {
        const res = await chatbotApi.sendMessage(sessionId, text);
        // understood: false여도 reply에 같은 질문이 오므로 그대로 표시한다
        setMessages((prev) => [
          ...prev,
          { role: 'ASSISTANT', content: res.reply, createdAt: new Date().toISOString() },
        ]);
        setPendingFields(res.pendingFields);
        setRequiresConfirmation(res.requiresConfirmation);
        setStatus('ready');
        return { ok: true };
      } catch (e) {
        // 실패한 메시지는 목록에서 빼고, 입력창에 내용을 되돌려 놓도록 호출부에 알린다
        setMessages((prev) => prev.filter((m) => m !== userMessage));
        setStatus('ready');
        if (e instanceof ApiError && e.code === 'SESSION_CLOSED') {
          setError('이미 저장된 기록이에요. 총정리 화면으로 이동할게요.');
          return { ok: false, closed: true };
        }
        if (e instanceof ApiError && e.code === 'CHATBOT_UNAVAILABLE') {
          setError('잠시 후 다시 시도해 주세요.');
        } else {
          setError(e instanceof Error ? e.message : '메시지를 보내지 못했어요.');
        }
        return { ok: false };
      }
    },
    [sessionId],
  );

  return { sessionId, messages, pendingFields, requiresConfirmation, status, error, send, retry: start };
}
