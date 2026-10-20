import { useCallback, useEffect, useRef, useState } from 'react';
import { chatbotApi } from '@/apis/chatbot';
import { ApiError } from '@/apis/client';
import type { ChatChoiceRequest, ChatMessage, PendingField, SendMessageResponse } from '@/types/chatbot';
import { SKIN_SIGN_LABELS } from '@/utils/constants';
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

const SKIN_SIGN_ORDER = Object.keys(SKIN_SIGN_LABELS);

/** 선택지 답을 대화에 남길 문장 — 서버가 대화 내역에 저장하는 문장과 같다 */
export function choiceText(choice: ChatChoiceRequest): string {
  if ('skip' in choice) return choice.field === 'notes' ? '없어요' : '넘어갈게요';
  switch (choice.field) {
    case 'painIndex':
      return `${choice.value}점`;
    case 'sleepHours':
      return `${choice.value}시간`;
    case 'medication':
      return choice.value ? '예' : '아니오';
    case 'skinSigns':
      return choice.value.length === 0
        ? '없어요'
        : [...choice.value]
            .sort((a, b) => SKIN_SIGN_ORDER.indexOf(a) - SKIN_SIGN_ORDER.indexOf(b))
            .map((sign) => SKIN_SIGN_LABELS[sign])
            .join(', ');
  }
}

const FINISH_TEXT = '기록을 마칠게요';

/**
 * 기록(RECORD) 모드 챗봇 세션.
 * - 들어올 때 이용 동의부터 확인하고, 동의 전에는 세션을 만들지 않는다
 * - 세션을 요청하면 서버가 같은 날짜의 열린 세션을 돌려주므로, 대화 내역과 진행 상태(초안 조회 API)를 복원한다
 * - 그날 기록이 이미 저장되어 있으면 서버가 그 기록을 불러온 수정 세션을 열고, 첫 메시지로 수정할 내용을 묻는다
 * - 응답의 pendingFields / askingField / requiresConfirmation 상태를 노출
 * - 글(send), 선택지(choose), 기록 마치기(finish) 모두 같은 방식으로 한 턴을 주고받는다
 */
export function useChatSession(recordDate: string) {
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [pendingFields, setPendingFields] = useState<PendingField[]>([]);
  const [askingField, setAskingField] = useState<PendingField | null>(null);
  const [requiresConfirmation, setRequiresConfirmation] = useState(false);
  // 챗봇 캐릭터 표정에 쓴다. 아직 말하지 않았으면 null
  const [painIndex, setPainIndex] = useState<number | null>(null);
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

      // 서버가 이 날짜에 이어 쓸 세션을 정한다 — 열려 있던 대화는 그대로, 저장된 기록이 있으면 그 기록을 고치는 세션.
      // 그사이 총정리에서 기록을 고쳤으면 예전 초안 대신 새 세션을 주므로, 보관해 둔 sessionId를 그대로 믿지 않는다.
      const session = await chatbotApi.createSession('RECORD');
      chatSessionStore.set(recordDate, session.sessionId);
      const [{ messages }, state] = await Promise.all([
        chatbotApi.getMessages(session.sessionId),
        chatbotApi.getDraft(session.sessionId),
      ]);
      setSessionId(session.sessionId);
      setMessages(messages);
      setPendingFields(state.pendingFields);
      setAskingField(state.askingField);
      setRequiresConfirmation(state.requiresConfirmation);
      setPainIndex(state.draft.painIndex ?? null);
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

  /** 사용자 말을 먼저 보여 주고 요청을 보낸다. 실패하면 그 말을 목록에서 뺀다 */
  const runTurn = useCallback(
    async (text: string, request: (sessionId: string) => Promise<SendMessageResponse>): Promise<SendResult> => {
      if (!sessionId) return { ok: false };
      const userMessage: ChatMessage = { role: 'USER', content: text, createdAt: new Date().toISOString() };
      setMessages((prev) => [...prev, userMessage]);
      setStatus('sending');
      setError(null);
      try {
        const res = await request(sessionId);
        // understood: false여도 reply에 같은 질문(필요하면 입력 예시 포함)이 오므로 그대로 표시한다
        setMessages((prev) => [
          ...prev,
          { role: 'ASSISTANT', content: res.reply, createdAt: new Date().toISOString() },
        ]);
        setPendingFields(res.pendingFields);
        setAskingField(res.askingField);
        setRequiresConfirmation(res.requiresConfirmation);
        setPainIndex(res.draft?.painIndex ?? null);
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

  const send = useCallback(
    (text: string) => runTurn(text, (id) => chatbotApi.sendMessage(id, text)),
    [runTurn],
  );

  /** 선택지 답 — LLM을 거치지 않으므로 이용 한도와 상관없이 보낼 수 있다 */
  const choose = useCallback(
    async (choice: ChatChoiceRequest) => {
      const result = await runTurn(choiceText(choice), (id) => chatbotApi.sendChoice(id, choice));
      // 다른 탭에서 답했거나 해서 지금 묻는 항목이 바뀌었을 수 있다 — 서버 상태로 다시 맞춘다
      if (!result.ok && !result.closed && sessionId) {
        try {
          const state = await chatbotApi.getDraft(sessionId);
          setPendingFields(state.pendingFields);
          setAskingField(state.askingField);
          setRequiresConfirmation(state.requiresConfirmation);
          setPainIndex(state.draft.painIndex ?? null);
        } catch {
          // 다시 맞추지 못해도 오류 문구는 이미 보여 주고 있다
        }
      }
      return result;
    },
    [runTurn, sessionId],
  );

  const finish = useCallback(() => runTurn(FINISH_TEXT, (id) => chatbotApi.finish(id)), [runTurn]);

  return {
    sessionId,
    messages,
    pendingFields,
    askingField,
    requiresConfirmation,
    painIndex,
    status,
    error,
    limitReached,
    send,
    choose,
    finish,
    agree,
    retry: start,
  };
}
