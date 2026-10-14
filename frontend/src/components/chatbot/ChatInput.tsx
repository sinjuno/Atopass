import { useRef, type FormEvent } from 'react';
import { CHAT_MESSAGE_MAX_LENGTH, PHOTO_ACCEPT } from '@/utils/constants';

interface Props {
  value: string;
  onChange: (value: string) => void;
  onSubmit: (text: string) => void;
  onAttachPhotos: (files: File[]) => void;
  disabled?: boolean;
}

export default function ChatInput({ value, onChange, onSubmit, onAttachPhotos, disabled }: Props) {
  const fileRef = useRef<HTMLInputElement>(null);
  const trimmed = value.trim();

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!trimmed || disabled) return;
    onSubmit(trimmed);
  };

  return (
    <form onSubmit={handleSubmit} className="flex items-end gap-2 border-t border-gray-200 bg-white p-3">
      <button
        type="button"
        onClick={() => fileRef.current?.click()}
        className="shrink-0 rounded-full border border-gray-300 px-3 py-2 text-base text-gray-600"
        aria-label="사진 추가"
      >
        사진
      </button>
      <input
        ref={fileRef}
        type="file"
        accept={PHOTO_ACCEPT.join(',')}
        multiple
        hidden
        onChange={(e) => {
          onAttachPhotos(Array.from(e.target.files ?? []));
          e.target.value = '';
        }}
      />
      <textarea
        value={value}
        onChange={(e) => onChange(e.target.value)}
        maxLength={CHAT_MESSAGE_MAX_LENGTH}
        rows={1}
        placeholder="피부 상태를 말해 주세요"
        className="max-h-32 min-h-11 flex-1 resize-none rounded-2xl border border-gray-300 px-4 py-2 text-base focus:border-emerald-500 focus:outline-none"
        onKeyDown={(e) => {
          if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) handleSubmit(e);
        }}
      />
      <button
        type="submit"
        disabled={!trimmed || disabled}
        className="shrink-0 rounded-full bg-emerald-600 px-4 py-2 text-base font-semibold text-white disabled:bg-gray-300"
      >
        보내기
      </button>
    </form>
  );
}
