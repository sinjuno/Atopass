import { useState } from 'react';
import { CHAT_CONSENT_TEXT } from '@/utils/constants';

interface Props {
  onAgree: () => Promise<void>;
  /** 동의하지 않으면 챗봇 없이 총정리 화면에서 직접 입력한다 */
  onDecline: () => void;
  error?: string | null;
  /** 기본값은 챗봇 이용 동의 문구. 같은 동의를 다른 기능에서 안내할 때 바꿔 쓴다 */
  title?: string;
  lines?: string[];
  note?: string;
  declineLabel?: string;
}

/** 챗봇 이용 동의 팝업 — 동의 여부는 POST /chatbot/consent로 서버에 저장된다 */
export default function ConsentDialog({
  onAgree,
  onDecline,
  error,
  title = '챗봇 이용 동의',
  lines = CHAT_CONSENT_TEXT,
  note = '동의하지 않아도 기록 화면에서 직접 입력할 수 있어요.',
  declineLabel = '동의하지 않고 직접 입력하기',
}: Props) {
  const [submitting, setSubmitting] = useState(false);

  const handleAgree = async () => {
    setSubmitting(true);
    try {
      await onAgree();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="consent-title"
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 p-4 sm:items-center"
    >
      <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl">
        <h2 id="consent-title" className="mb-3 text-xl font-bold">
          {title}
        </h2>
        <div className="mb-5 flex flex-col gap-2 text-base text-gray-700">
          {lines.map((line) => (
            <p key={line}>{line}</p>
          ))}
          <p className="text-sm text-gray-500">{note}</p>
        </div>
        {error && (
          <p role="alert" className="mb-3 text-sm text-red-500">
            {error}
          </p>
        )}
        <div className="flex flex-col gap-2">
          <button
            type="button"
            onClick={() => void handleAgree()}
            disabled={submitting}
            className="rounded-xl bg-brand-600 py-3 text-lg font-bold text-white disabled:bg-gray-300"
          >
            {submitting ? '저장 중…' : '동의하고 시작하기'}
          </button>
          <button type="button" onClick={onDecline} className="py-3 text-base text-gray-600">
            {declineLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
