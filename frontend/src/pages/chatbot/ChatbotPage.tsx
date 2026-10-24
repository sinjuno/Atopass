import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import ChatChoices from '@/components/chatbot/ChatChoices';
import ChatInput from '@/components/chatbot/ChatInput';
import ConsentDialog from '@/components/chatbot/ConsentDialog';
import ChatMessageList from '@/components/chatbot/ChatMessageList';
import RecordCompleteOverlay from '@/components/chatbot/RecordCompleteOverlay';
import PageHeader from '@/components/common/PageHeader';
import BodyMapSummary from '@/components/record/BodyMapSummary';
import PendingPhotoList from '@/components/record/PendingPhotoList';
import { useChatSession } from '@/hooks/useChatSession';
import type { ChatChoiceRequest, ChoiceField } from '@/types/chatbot';
import { useRecordDraft } from '@/hooks/useRecordDraft';
import { atomiMood } from '@/utils/atomiMood';
import { formatKoreanDate, today } from '@/utils/date';
import { validatePhotos } from '@/utils/photo';
import { recordPath } from '@/utils/routes';

/** 서버가 기록을 저장·수정하고 총정리를 보여 줄 때 쓰는 문장 (ChatbotService.SAVED, EDIT_SAVED) */
const SUMMARY_MARKERS = ['오늘의 총정리예요', '지금 기록은 아래와 같아요'];

/** 기록 (챗봇) — /chatbot. 대화형으로 오늘의 피부 기록을 입력한다 */
export default function ChatbotPage() {
  const navigate = useNavigate();
  const recordDate = today();
  const {
    messages,
    askingField,
    requiresConfirmation,
    chatDraft,
    painIndex,
    understood,
    status,
    error,
    limitReached,
    send,
    choose,
    recordId,
    uploadPhotos,
    agree,
    retry,
    justCompleted,
    dismissCompletion,
  } = useChatSession(recordDate);
  const draft = useRecordDraft(recordDate);
  const [input, setInput] = useState('');
  const [photoError, setPhotoError] = useState<string | null>(null);
  // 답할 때마다 늘려서 선택지를 새로 만든다 — 다음 질문의 선택지가 다시 아래에서 올라오게
  const [choiceRound, setChoiceRound] = useState(0);
  // 선택지를 내려 두면 글로 답할 수 있고, 펼쳐 두면 선택지로만 답한다
  const [choicesMinimized, setChoicesMinimized] = useState(false);

  const goToSummary = () => navigate(recordPath(recordDate));

  const handleConfirmComplete = () => {
    dismissCompletion();
    goToSummary();
  };

  // 서버가 정한 항목(askingField)을 선택지로 묻는다. 부위·증상 점수(symptoms)는 글로 답한다.
  // 첫 화면에서는 고통 지수 막대와 함께 입력창도 열어 두어, 편하게 글로 시작할 수도 있다.
  // (LLM이 응답하지 못해도 버튼만으로 끝까지 기록할 수 있다)
  const started = messages.length > 0;
  const choiceField: ChoiceField | null =
    !requiresConfirmation && askingField && askingField !== 'symptoms' ? askingField : null;
  // 특이사항을 직접 적는 단계는 글로 답해야 하므로 입력창을 그대로 둔다.
  // 예시 사진을 보여 줄지 묻는 단계도 글로 다른 말을 할 수 있게 둔다 (그러면 사진은 보여 주지 않는다).
  // 그 밖의 선택지 단계는 어차피 글로 답하지 않으므로 아래 입력창 자체를 숨긴다.
  // 선택지를 내려 두면(choicesMinimized) 다시 보인다.
  const hideInput =
    started && choiceField !== null && choiceField !== 'notes' && choiceField !== 'signImage' && !choicesMinimized;

  // 가장 최근의 일일 총정리(저장·수정 안내) 말풍선 아래에, 부위마다 나타난 증상 개수만큼 칠한 마네킹을 붙인다.
  // 저장한 뒤에는 고칠 때마다 바로 다시 저장하므로, 지금 초안이 곧 저장된 기록이다
  const summaryIndex = messages
    .map((m) => m.role === 'ASSISTANT' && SUMMARY_MARKERS.some((marker) => m.content.includes(marker)))
    .lastIndexOf(true);
  const skinConditions = chatDraft?.skinConditions ?? [];
  const summaryMap =
    recordId && summaryIndex >= 0 && skinConditions.length > 0
      ? {
          index: summaryIndex,
          content: (
            <div className="rounded-2xl border border-gray-200 bg-white p-3">
              <p className="mb-2 text-sm font-semibold text-gray-700">부위별 증상</p>
              <BodyMapSummary skinConditions={skinConditions} />
            </div>
          ),
        }
      : undefined;

  // 기록이 저장되기 전에 붙여 둔 사진은, 저장되는 대로 그날 기록에 올린다 (저장된 뒤에 붙인 사진은 바로 올린다)
  const pendingPhotos = draft.photos;
  useEffect(() => {
    if (!recordId || pendingPhotos.length === 0) return;
    draft.clearPhotos();
    void uploadPhotos(pendingPhotos).then((failed) => {
      if (failed.length) draft.addPhotos(failed);
    });
  }, [recordId, pendingPhotos]);

  // 다른 탭 등에서 이 대화가 이미 저장됐으면(closed) 총정리로 보내지 않고, 저장된 기록을 고치는 대화로 다시 연다
  const handleSubmit = async (text: string) => {
    setInput('');
    const result = await send(text);
    if (result.ok) return;
    setInput(text); // 실패하면 입력한 내용을 유지한다
    if (result.closed) void retry();
  };

  // 선택지는 답을 고르면 스스로 내려간 뒤 여기로 답을 넘긴다
  const handleChoose = async (choice: ChatChoiceRequest) => {
    const result = await choose(choice);
    setChoiceRound((n) => n + 1);
    if (result.closed) void retry();
  };

  const handleAttach = (files: File[]) => {
    const message = validatePhotos(files, draft.photos.length);
    setPhotoError(message);
    if (!message) draft.addPhotos(files);
  };

  return (
    <div className="flex h-[calc(100dvh-4rem)] flex-col bg-[#f5f5f5]">
      <PageHeader
        title="오늘의 기록"
        right={<span className="text-base text-gray-500">{formatKoreanDate(recordDate)}</span>}
      />

      {/* 아래 선택지 칸이 길어도 대화가 가려지지 않도록 최소 높이를 둔다. 마네킹은 키가 커서 대화를 조금 더 내어 준다 */}
      <div className={`${choiceField === 'bodyParts' ? 'min-h-12' : 'min-h-32'} flex-1 overflow-y-auto`}>
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
            mood={atomiMood(painIndex, requiresConfirmation, understood)}
            attachment={summaryMap}
          />
        )}
      </div>


      {/* 질문에 맞는 선택지(막대·버튼)가 아래에서 올라온다. 답을 보내는 동안에는 내려가 있고, 다음 질문과 함께 다시 올라온다 */}
      {choiceField && status === 'ready' && (
        <ChatChoices
          key={`${choiceField}:${choiceRound}`}
          field={choiceField}
          onChoose={(c) => void handleChoose(c)}
          skinSigns={chatDraft?.skinSigns ?? []}
          onMinimizedChange={setChoicesMinimized}
          onAttachPhotos={handleAttach}
        />
      )}

      {(draft.photos.length > 0 || photoError) && (
        <div className="border-t border-gray-100 px-3 pt-3">
          <PendingPhotoList files={draft.photos} onRemove={draft.removePhoto} />
          {photoError && <p className="mt-2 text-sm text-red-500">{photoError}</p>}
        </div>
      )}

      {limitReached && (
        <div role="alert" className="border-t border-amber-100 bg-amber-50 p-3">
          <p className="text-center text-base text-gray-700">
            오늘 챗봇 이용 한도에 도달했어요. 글로 답하는 것은 잠시 뒤에 다시 해 주세요. 선택지 버튼은 계속 쓸 수 있어요
          </p>
        </div>
      )}

      {(status === 'ready' || status === 'sending') && error && (
        <p role="alert" className="px-4 pt-2 text-sm text-red-500">
          {error}
        </p>
      )}

      {!hideInput && (
        <ChatInput
          value={input}
          onChange={setInput}
          onSubmit={handleSubmit}
          onAttachPhotos={handleAttach}
          disabled={status !== 'ready'}
        />
      )}

      {status === 'consent' && <ConsentDialog onAgree={agree} onDecline={goToSummary} error={error} />}
      {justCompleted && <RecordCompleteOverlay onConfirm={handleConfirmComplete} onContinue={dismissCompletion} />}
    </div>
  );
}
