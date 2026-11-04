import { useEffect, useRef, useState } from 'react';
import { chatbotApi } from '@/apis/chatbot';
import { ApiError } from '@/apis/client';
import { medicationApi } from '@/apis/medication';
import ConsentDialog from '@/components/chatbot/ConsentDialog';
import PendingPhotoList from '@/components/record/PendingPhotoList';
import { LoadingMessage } from '@/components/common/StatusMessage';
import type { ExtractedMedication, MedicationInput } from '@/types/medication';
import { today } from '@/utils/date';
import { MED_PHOTO_MAX_COUNT, medicationErrorMessage, validateMedicationPhotos } from '@/utils/medication';
import MedicationReviewCard from './MedicationReviewCard';

interface Props {
  /** 저장이 끝났을 때(저장한 개수) */
  onSaved: (count: number) => void;
  onCancel: () => void;
  /** 사진 인식이 안 될 때 직접 입력으로 넘어가는 길 */
  onManual: () => void;
}

interface ReviewItem {
  key: number;
  checked: boolean;
  value: ExtractedMedication;
}

/** 복약 안내문 사진 → 인식 결과 확인 → 저장. 회원가입 단계·마이페이지 공용 */
export default function MedicationPhotoFlow({ onSaved, onCancel, onManual }: Props) {
  const fileRef = useRef<HTMLInputElement>(null);
  const [files, setFiles] = useState<File[]>([]);
  const [stage, setStage] = useState<'checking' | 'consent' | 'pick' | 'uploading' | 'review'>('checking');
  const [items, setItems] = useState<ReviewItem[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [showErrors, setShowErrors] = useState(false);
  const [saving, setSaving] = useState(false);

  // 사진은 외부 AI로 전달되므로 챗봇 이용 동의를 한 사용자만 쓸 수 있다. 동의가 없으면 안내를 먼저 보여 준다.
  // 확인 자체가 실패하면 막지 않고 진행한다(서버가 403 CONSENT_REQUIRED 로 다시 막는다).
  useEffect(() => {
    let cancelled = false;
    chatbotApi
      .getConsent()
      .then((consent) => !cancelled && setStage(consent.agreed ? 'pick' : 'consent'))
      .catch(() => !cancelled && setStage('pick'));
    return () => {
      cancelled = true;
    };
  }, []);

  const handleAgree = async () => {
    try {
      await chatbotApi.agreeConsent();
      setError(null);
      setStage('pick');
    } catch (e) {
      setError(medicationErrorMessage(e));
    }
  };

  const addFiles = (picked: File[]) => {
    const next = [...files, ...picked];
    const message = validateMedicationPhotos(next);
    setError(message);
    if (!message) setFiles(next);
  };

  const handleExtract = async () => {
    setError(null);
    setStage('uploading');
    try {
      const result = await medicationApi.extract(files);
      if (result.length === 0) {
        // 422 와 같은 상황: 하나도 못 읽음
        setError('잘 안 보여요. 다시 찍거나 직접 입력해 주세요.');
        setStage('pick');
        return;
      }
      setItems(
        result.map((value, key) => ({ key, checked: true, value: { ...value, startDate: value.startDate || today() } })),
      );
      setShowErrors(false);
      setStage('review');
    } catch (e) {
      if (e instanceof ApiError && e.code === 'CONSENT_REQUIRED') {
        // 다른 기기에서 동의를 철회한 경우 등
        setError(null);
        setStage('consent');
        return;
      }
      setError(medicationErrorMessage(e));
      setStage('pick');
    }
  };

  const updateItem = (key: number, patch: Partial<ReviewItem>) =>
    setItems((prev) => prev.map((it) => (it.key === key ? { ...it, ...patch } : it)));

  const selected = items.filter((it) => it.checked);

  const handleSave = async () => {
    if (saving || selected.length === 0) return;
    if (selected.some((it) => !it.value.name?.trim() || !it.value.type || !it.value.startDate)) {
      setShowErrors(true);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const payload = selected.map(({ value }): MedicationInput => ({
        ...value,
        name: value.name!.trim(),
        type: value.type!,
      }));
      await medicationApi.bulkCreate(payload, 'photo');
      onSaved(payload.length);
    } catch (e) {
      setError(medicationErrorMessage(e));
    } finally {
      setSaving(false);
    }
  };

  if (stage === 'checking') {
    return <LoadingMessage />;
  }

  if (stage === 'consent') {
    return (
      <ConsentDialog
        title="사진 인식 이용 동의"
        lines={[
          '복약 안내문 사진이 약 정보를 읽기 위해 외부 AI(Claude)로 전달돼요.',
          '챗봇 이용 동의와 같은 동의예요. 이메일 같은 식별정보는 전달되지 않아요.',
        ]}
        note="동의하지 않아도 약을 직접 입력할 수 있어요."
        onAgree={handleAgree}
        onDecline={onManual}
        error={error}
      />
    );
  }

  if (stage === 'uploading') {
    return <LoadingMessage message="복약 안내문을 읽고 있어요…" />;
  }

  if (stage === 'review') {
    return (
      <div className="flex flex-col gap-4">
        <p className="text-gray-700">읽어 온 내용이에요. 틀린 곳은 바로 고치고, 저장할 약을 골라 주세요.</p>
        <ul className="flex flex-col gap-3">
          {items.map((it, i) => (
            <MedicationReviewCard
              key={it.key}
              index={i}
              checked={it.checked}
              value={it.value}
              showErrors={showErrors}
              onCheckedChange={(checked) => updateItem(it.key, { checked })}
              onChange={(value) => updateItem(it.key, { value })}
            />
          ))}
        </ul>
        {error && (
          <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-red-700">
            {error}
          </p>
        )}
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => {
              setStage('pick');
              setError(null);
            }}
            className="flex-1 rounded-2xl border border-gray-200 bg-white py-3 font-bold text-gray-600"
          >
            다시 찍기
          </button>
          <button
            type="button"
            onClick={() => void handleSave()}
            disabled={saving || selected.length === 0}
            className="flex-1 rounded-2xl bg-brand-600 py-3 font-bold text-white disabled:opacity-40"
          >
            {saving ? '저장 중…' : `저장 (${selected.length})`}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <p className="rounded-xl bg-brand-50 px-4 py-3 text-brand-700">
        약국에서 받은 복약 안내문이나 약봉투를 찍어주세요. 연고는 상자나 튜브를 함께 찍어도 돼요. 이름·주민등록번호는
        가려주세요.
      </p>

      <PendingPhotoList files={files} onRemove={(i) => setFiles((prev) => prev.filter((_, idx) => idx !== i))} />

      <input
        ref={fileRef}
        type="file"
        accept="image/*"
        capture="environment"
        multiple
        hidden
        onChange={(e) => {
          addFiles(Array.from(e.target.files ?? []));
          e.target.value = '';
        }}
      />
      <button
        type="button"
        onClick={() => fileRef.current?.click()}
        disabled={files.length >= MED_PHOTO_MAX_COUNT}
        className="rounded-xl border border-dashed border-gray-300 py-3 text-base text-gray-600 disabled:opacity-40"
      >
        📷 {files.length === 0 ? '사진 찍기' : `사진 추가 (${files.length}/${MED_PHOTO_MAX_COUNT})`}
      </button>

      {error && (
        <div role="alert" className="flex flex-col gap-2 rounded-xl bg-red-50 px-4 py-3 text-red-700">
          <p>{error}</p>
          <button type="button" onClick={onManual} className="self-start font-bold underline">
            직접 입력하기
          </button>
        </div>
      )}

      <div className="flex gap-2">
        <button
          type="button"
          onClick={onCancel}
          className="flex-1 rounded-2xl border border-gray-200 bg-white py-3 font-bold text-gray-600"
        >
          취소
        </button>
        <button
          type="button"
          onClick={() => void handleExtract()}
          disabled={files.length === 0}
          className="flex-1 rounded-2xl bg-brand-600 py-3 font-bold text-white disabled:opacity-40"
        >
          약 읽어오기
        </button>
      </div>
    </div>
  );
}
