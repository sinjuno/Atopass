import { useEffect, useRef, type ReactNode } from 'react';
import type { ChatMessage } from '@/types/chatbot';

interface Props {
  messages: ChatMessage[];
  typing?: boolean;
}

export default function ChatMessageList({ messages, typing }: Props) {
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length, typing]);

  return (
    <div className="flex flex-col gap-3 px-4 py-4" aria-live="polite">
      {messages.length === 0 && (
        <ChatBubble role="ASSISTANT">
          오늘 피부 상태는 어땠나요? 편하게 말씀해 주세요.
          {'\n'}예) "오늘 팔이 많이 가렵고 잠은 6시간 잤어"
        </ChatBubble>
      )}
      {messages.map((m, i) => (
        <ChatBubble key={`${m.createdAt}-${i}`} role={m.role}>
          {m.content}
        </ChatBubble>
      ))}
      {typing && <ChatBubble role="ASSISTANT">입력 중…</ChatBubble>}
      <div ref={bottomRef} />
    </div>
  );
}

function ChatBubble({ role, children }: { role: ChatMessage['role']; children: ReactNode }) {
  const isUser = role === 'USER';
  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}>
      <p
        className={`max-w-[80%] whitespace-pre-wrap rounded-2xl px-4 py-3 text-base leading-relaxed ${
          isUser ? 'rounded-br-sm bg-emerald-600 text-white' : 'rounded-bl-sm bg-gray-100 text-gray-900'
        }`}
      >
        {children}
      </p>
    </div>
  );
}
