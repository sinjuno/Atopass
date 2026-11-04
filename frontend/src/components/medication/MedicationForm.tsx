import { useState, type FormEvent } from 'react';
import TextField from '@/components/common/TextField';
import type { Medication, MedicationInput, MedicationTime, MedicationType } from '@/types/medication';
import { today } from '@/utils/date';
import { REQUIRED_MESSAGE, TimeButtons, TypeButtons } from './MedicationFields';

interface Props {
  /** 수정할 때 기존 값. 없으면 새로 입력(시작일 기본 오늘). */
  initial?: Medication;
  submitLabel?: string;
  /** 저장 요청. 실패 메시지는 던진 쪽에서 error 로 내려준다. */
  onSubmit: (values: MedicationInput) => Promise<void>;
  onCancel?: () => void;
  error?: string | null;
}

/** 약 입력/수정 폼 — 회원가입 단계·마이페이지 공용 */
export default function MedicationForm({ initial, submitLabel = '저장', onSubmit, onCancel, error }: Props) {
  const [name, setName] = useState(initial?.name ?? '');
  const [type, setType] = useState<MedicationType | null>(initial?.type ?? null);
  const [dose, setDose] = useState(initial?.dose ?? '');
  const [times, setTimes] = useState<MedicationTime[]>(initial?.times ?? []);
  const [startDate, setStartDate] = useState(initial?.startDate ?? today());
  const [memo, setMemo] = useState(initial?.memo ?? '');
  const [isOtherCondition, setIsOtherCondition] = useState(initial?.isOtherCondition ?? false);
  const [errors, setErrors] = useState<{ name?: string; type?: string; startDate?: string }>({});
  const [saving, setSaving] = useState(false);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (saving) return;
    const next = {
      name: name.trim() ? undefined : REQUIRED_MESSAGE,
      type: type ? undefined : REQUIRED_MESSAGE,
      startDate: startDate ? undefined : REQUIRED_MESSAGE,
    };
    setErrors(next);
    if (next.name || next.type || next.startDate || !type) return;

    setSaving(true);
    try {
      await onSubmit({
        name: name.trim(),
        type,
        dose: dose.trim() || null,
        times,
        timesText: initial?.timesText ?? null,
        startDate,
        endDate: initial?.endDate ?? null,
        memo: memo.trim() || null,
        isOtherCondition,
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4">
      <TextField
        label="약 이름"
        value={name}
        onChange={(e) => setName(e.target.value)}
        error={errors.name}
        autoComplete="off"
      />

      <div>
        <p className="mb-1.5 font-medium text-gray-700">종류</p>
        <TypeButtons value={type} onChange={setType} />
        {errors.type && <p className="mt-1.5 text-sm text-red-600">{errors.type}</p>}
      </div>

      <TextField
        label="1회 용량 (선택)"
        value={dose}
        onChange={(e) => setDose(e.target.value)}
        placeholder="예: 1정, 10mg, 얇게"
        autoComplete="off"
      />

      <div>
        <p className="mb-1.5 font-medium text-gray-700">복용 시간 (선택)</p>
        <TimeButtons value={times} onChange={setTimes} />
        {initial?.timesText && <p className="mt-1.5 text-sm text-gray-500">안내문 원문: {initial.timesText}</p>}
      </div>

      <TextField
        label="시작일"
        type="date"
        value={startDate}
        onChange={(e) => setStartDate(e.target.value)}
        error={errors.startDate}
      />

      <TextField label="메모 (선택)" value={memo} onChange={(e) => setMemo(e.target.value)} autoComplete="off" />

      <label className="flex items-center gap-3 text-base text-gray-700">
        <input
          type="checkbox"
          checked={isOtherCondition}
          onChange={(e) => setIsOtherCondition(e.target.checked)}
          className="size-5 accent-brand-600"
        />
        다른 지병 때문에 먹는 약이에요
      </label>

      {error && (
        <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-red-700">
          {error}
        </p>
      )}

      <div className="flex gap-2">
        {onCancel && (
          <button
            type="button"
            onClick={onCancel}
            className="flex-1 rounded-2xl border border-gray-200 bg-white py-3 font-bold text-gray-600"
          >
            취소
          </button>
        )}
        <button
          type="submit"
          disabled={saving}
          className="flex-1 rounded-2xl bg-brand-600 py-3 font-bold text-white disabled:opacity-40"
        >
          {saving ? '저장 중…' : submitLabel}
        </button>
      </div>
    </form>
  );
}
