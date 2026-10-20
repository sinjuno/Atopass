import { Fragment, useEffect, useRef, type ReactNode } from 'react';
import AtomiAvatar from '@/components/chatbot/AtomiAvatar';
import type { ChatMessage } from '@/types/chatbot';
import type { AtomiMood } from '@/utils/atomiMood';

interface Props {
  messages: ChatMessage[];
  typing?: boolean;
  /** 지금 아토미의 표정 — 가장 최근 챗봇 말풍선에만 보여 주고, 지난 말풍선은 기본 표정이다 */
  mood?: AtomiMood;
  /** 이 순서의 말풍선 바로 아래에 붙여 보여 줄 내용 (일일 총정리의 부위별 증상 마네킹) */
  attachment?: { index: number; content: ReactNode };
}

export default function ChatMessageList({ messages, typing, mood = 'default', attachment }: Props) {
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length, typing]);

  // 입력 중 말풍선이 있으면 그것이 가장 최근 챗봇 말풍선이다
  const latestAssistant = typing ? -1 : messages.map((m) => m.role).lastIndexOf('ASSISTANT');

  return (
    <div className="flex flex-col gap-3 px-4 py-4" aria-live="polite">
      {messages.length === 0 && (
        <ChatBubble role="ASSISTANT" mood={mood}>
          오늘 피부 상태는 어땠나요? 편하게 말씀해 주시거나, 아래에서 막대를 좌우로 밀어 오늘 느낀 고통 지수를 골라 주세요.
          {'\n'}예) "오늘 팔이 많이 가렵고 잠은 6시간 잤어"
        </ChatBubble>
      )}
      {messages.map((m, i) => (
        <Fragment key={`${m.createdAt}-${i}`}>
          <ChatBubble role={m.role} mood={i === latestAssistant ? mood : 'default'}>
            {m.content}
          </ChatBubble>
          {attachment?.index === i && <div className="ml-12 max-w-[75%]">{attachment.content}</div>}
        </Fragment>
      ))}
      {typing && (
        <ChatBubble role="ASSISTANT" mood={mood}>
          입력 중…
        </ChatBubble>
      )}
      <div ref={bottomRef} />
    </div>
  );
}

function ChatBubble({
  role,
  mood = 'default',
  children,
}: {
  role: ChatMessage['role'];
  mood?: AtomiMood;
  children: ReactNode;
}) {
  const isUser = role === 'USER';
  return (
    <div className={`flex items-end gap-2 ${isUser ? 'justify-end' : 'justify-start'}`}>
      {!isUser && <AtomiAvatar mood={mood} />}
      <p
        className={`max-w-[75%] whitespace-pre-wrap rounded-2xl px-4 py-3 text-base leading-relaxed ${
          isUser ? 'rounded-br-sm bg-emerald-600 text-white' : 'rounded-bl-sm bg-gray-100 text-gray-900'
        }`}
      >
        {children}
      </p>
    </div>
  );
}
