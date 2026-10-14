import { useState } from 'react';
import { useNavigate } from 'react-router';
import ChatInput from '@/components/chatbot/ChatInput';
import ChatMessageList from '@/components/chatbot/ChatMessageList';
import PageHeader from '@/components/common/PageHeader';
import PendingPhotoList from '@/components/record/PendingPhotoList';
import { useChatSession } from '@/hooks/useChatSession';
import { useRecordDraft } from '@/hooks/useRecordDraft';
import { formatKoreanDate, today } from '@/utils/date';
import { validatePhotos } from '@/utils/photo';
import { recordPath } from '@/utils/routes';

/** 기록 (챗봇) — /chatbot. 대화형으로 오늘의 피부 기록을 입력한다 */
export default function ChatbotPage() {
  const navigate = useNavigate();
  const recordDate = today();
  const { messages, requiresConfirmation, status, error, send, retry } = useChatSession(recordDate);
  const draft = useRecordDraft(recordDate);
  const [input, setInput] = useState('');
  const [photoError, setPhotoError] = useState<string | null>(null);

  const goToSummary = () => navigate(recordPath(recordDate));

  const handleSubmit = async (text: string) => {
    setInput('');
    const result = await send(text);
    if (result.ok) return;
    setInput(text); // 실패하면 입력한 내용을 유지한다
    if (result.closed) goToSummary();
  };

  const handleAttach = (files: File[]) => {
    const message = validatePhotos(files, draft.photos.length);
    setPhotoError(message);
    if (!message) draft.addPhotos(files);
  };

  return (
    <div className="flex h-[calc(100dvh-4rem)] flex-col">
      <PageHeader
        title="오늘의 기록"
        right={<span className="text-base text-gray-500">{formatKoreanDate(recordDate)}</span>}
      />

      <div className="flex-1 overflow-y-auto">
        {status === 'loading' && <p className="p-6 text-center text-gray-500">대화를 준비하고 있어요…</p>}
        {status === 'error' ? (
          <div className="p-6 text-center">
            <p className="mb-3 text-gray-700">{error}</p>
            <button type="button" onClick={() => void retry()} className="rounded-lg border px-4 py-2">
              다시 시도
            </button>
          </div>
        ) : (
          <ChatMessageList messages={messages} typing={status === 'sending'} />
        )}
      </div>

      {requiresConfirmation && (
        <div className="border-t border-emerald-100 bg-emerald-50 p-3">
          <button
            type="button"
            onClick={goToSummary}
            className="w-full rounded-xl bg-emerald-600 py-3 text-lg font-bold text-white"
          >
            기록 내용 확인하기
          </button>
        </div>
      )}

      {(draft.photos.length > 0 || photoError) && (
        <div className="border-t border-gray-100 px-3 pt-3">
          <PendingPhotoList files={draft.photos} onRemove={draft.removePhoto} />
          {photoError && <p className="mt-2 text-sm text-red-500">{photoError}</p>}
        </div>
      )}

      {status !== 'error' && status !== 'loading' && error && (
        <p role="alert" className="px-4 pt-2 text-sm text-red-500">
          {error}
        </p>
      )}

      <ChatInput
        value={input}
        onChange={setInput}
        onSubmit={handleSubmit}
        onAttachPhotos={handleAttach}
        disabled={status !== 'ready'}
      />
    </div>
  );
}
