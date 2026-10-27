import { Fragment, useEffect, useRef, useState, type ReactNode } from 'react';
import { chatbotApi } from '@/apis/chatbot';
import AtomiAvatar from '@/components/chatbot/AtomiAvatar';
import type { ChatMessage } from '@/types/chatbot';
import type { SkinSign } from '@/types/record';
import { SKIN_SIGN_LABELS } from '@/utils/constants';
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
          오늘 피부 상태는 어땠나요? 편하게 말씀해 주시거나, 아래에서 막대를 좌우로 밀어 오늘 느낀 불편함 지수를 골라 주세요.
          {'\n'}예) "오늘 팔이 많이 가렵고 잠은 6시간 잤어"
        </ChatBubble>
      )}
      {messages.map((m, i) => (
        <Fragment key={`${m.createdAt}-${i}`}>
          <ChatBubble role={m.role} mood={i === latestAssistant ? mood : 'default'}>
            {m.imageSign && <SkinSignImage sign={m.imageSign} />}
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
      <div
        className={`max-w-[75%] whitespace-pre-wrap rounded-2xl px-4 py-3 text-base leading-relaxed ${
          isUser ? 'rounded-br-sm bg-brand-100 text-gray-900' : 'rounded-bl-sm bg-brand-50 text-gray-900'
        }`}
      >
        {children}
      </div>
    </div>
  );
}

/** 증상 예시 사진의 출처. 사진 바로 아래에 작게 보여 준다 */
const SKIN_SIGN_IMAGE_SOURCE = '출처: 대한아토피피부염학회, 아토피인사이드';

/** 사용자가 보여 달라고 답한 증상 예시 사진. 인증이 필요해 blob으로 받아 보여 준다 */
function SkinSignImage({ sign }: { sign: SkinSign }) {
  const [url, setUrl] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let objectUrl: string | null = null;
    let cancelled = false;
    chatbotApi
      .getSkinSignImageUrl(sign)
      .then((u) => {
        objectUrl = u;
        if (cancelled) URL.revokeObjectURL(u);
        else setUrl(u);
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      });
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [sign]);

  if (failed) return <p className="mb-2 text-sm text-gray-500">사진을 불러오지 못했어요.</p>;
  if (!url) return <div className="mb-2 aspect-[4/3] w-full animate-pulse rounded-xl bg-gray-200" aria-hidden />;
  return (
    <figure className="mb-2">
      <img src={url} alt={`${SKIN_SIGN_LABELS[sign]} 예시 사진`} className="w-full rounded-xl border border-gray-200 bg-white" />
      <figcaption className="mt-1 text-xs leading-snug text-gray-500">{SKIN_SIGN_IMAGE_SOURCE}</figcaption>
    </figure>
  );
}
