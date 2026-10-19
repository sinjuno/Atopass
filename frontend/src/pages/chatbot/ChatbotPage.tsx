import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import ChatChoices from '@/components/chatbot/ChatChoices';
import ChatInput from '@/components/chatbot/ChatInput';
import ConsentDialog from '@/components/chatbot/ConsentDialog';
import ChatMessageList from '@/components/chatbot/ChatMessageList';
import PendingFieldList from '@/components/chatbot/PendingFieldList';
import PageHeader from '@/components/common/PageHeader';
import PendingPhotoList from '@/components/record/PendingPhotoList';
import { useChatSession } from '@/hooks/useChatSession';
import type { ChatChoiceRequest, ChoiceField } from '@/types/chatbot';
import { useRecordDraft } from '@/hooks/useRecordDraft';
import { atomiMood } from '@/utils/atomiMood';
import { formatKoreanDate, today } from '@/utils/date';
import { validatePhotos } from '@/utils/photo';
import { recordPath } from '@/utils/routes';

/** 기록 (챗봇) — /chatbot. 대화형으로 오늘의 피부 기록을 입력한다 */
export default function ChatbotPage() {
  const navigate = useNavigate();
  const recordDate = today();
  const {
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
    retry,
  } = useChatSession(recordDate);
  const draft = useRecordDraft(recordDate);
  const [input, setInput] = useState('');
  const [photoError, setPhotoError] = useState<string | null>(null);

  const goToSummary = () => navigate(recordPath(recordDate));

  // 서버가 정한 항목(askingField)을 선택지로 묻는다. 부위·증상 점수(symptoms)는 글로 답한다.
  // 첫 화면에서는 고통 지수 막대와 함께 입력창도 열어 두어, 편하게 글로 시작할 수도 있다.
  // (LLM이 응답하지 못해도 버튼만으로 끝까지 기록할 수 있다)
  const started = messages.length > 0;
  const choiceField: ChoiceField | null =
    !requiresConfirmation && askingField && askingField !== 'symptoms' ? askingField : null;
  // 선택지로 답하는 질문이거나 확인 단계이면 글을 보내지 못하게 한다 (사진은 계속 추가할 수 있다)
  const textLocked = (started && choiceField !== null && choiceField !== 'notes') || requiresConfirmation;
  // 부위를 고르는 동안에는 마네킹에 집중하도록 남은 항목과 "기록 마치기"를 숨긴다
  const pickingParts = choiceField === 'bodyParts';
  const canFinish =
    started &&
    !requiresConfirmation &&
    !pickingParts &&
    !pendingFields.includes('painIndex') &&
    !pendingFields.includes('symptoms');

  // 이 날짜의 대화가 이미 기록으로 저장됐으면 총정리(수정)로 보낸다
  useEffect(() => {
    if (status === 'closed') navigate(recordPath(recordDate), { replace: true });
  }, [status, navigate, recordDate]);

  const handleSubmit = async (text: string) => {
    setInput('');
    const result = await send(text);
    if (result.ok) return;
    setInput(text); // 실패하면 입력한 내용을 유지한다
    if (result.closed) goToSummary();
  };

  const handleChoose = async (choice: ChatChoiceRequest) => {
    const result = await choose(choice);
    if (result.closed) goToSummary();
  };

  const handleFinish = async () => {
    const result = await finish();
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

      {/* 아래 선택지 칸이 길어도 대화가 가려지지 않도록 최소 높이를 둔다 */}
      <div className="min-h-32 flex-1 overflow-y-auto">
        {status === 'loading' && <p className="p-6 text-center text-gray-500">대화를 준비하고 있어요…</p>}
        {status === 'error' ? (
          <div className="p-6 text-center">
            <p className="mb-3 text-gray-700">{error}</p>
            <button type="button" onClick={() => void retry()} className="rounded-lg border px-4 py-2">
              다시 시도
            </button>
          </div>
        ) : (
          <ChatMessageList
            messages={messages}
            typing={status === 'sending'}
            mood={atomiMood(painIndex, requiresConfirmation)}
          />
        )}
      </div>

      {!requiresConfirmation && !pickingParts && <PendingFieldList fields={pendingFields} />}

      {canFinish && (
        <div className="flex justify-end px-3 pb-1">
          <button
            type="button"
            onClick={() => void handleFinish()}
            disabled={status !== 'ready'}
            className="text-sm font-semibold text-emerald-700 underline underline-offset-2 disabled:opacity-40"
          >
            남은 항목 건너뛰고 기록 마치기
          </button>
        </div>
      )}

      {choiceField && (status === 'ready' || status === 'sending') && (
        <ChatChoices field={choiceField} onChoose={(c) => void handleChoose(c)} disabled={status !== 'ready'} />
      )}

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

      {limitReached && (
        <div role="alert" className="border-t border-amber-100 bg-amber-50 p-3">
          <p className="mb-2 text-center text-base text-gray-700">
            오늘 챗봇 이용 한도에 도달했어요. 총정리 화면에서 직접 입력할 수 있어요
          </p>
          <button
            type="button"
            onClick={goToSummary}
            className="w-full rounded-xl border border-amber-300 bg-white py-3 text-lg font-bold text-gray-800"
          >
            총정리에서 직접 입력하기
          </button>
        </div>
      )}

      {(status === 'ready' || status === 'sending') && error && (
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
        textLocked={textLocked}
      />

      {status === 'consent' && <ConsentDialog onAgree={agree} onDecline={goToSummary} error={error} />}
    </div>
  );
}
