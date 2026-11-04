import type { MedicationTime, MedicationType } from '@/types/medication';
import { MEDICATION_TIMES, MEDICATION_TYPES } from '@/utils/medication';

const chip = (selected: boolean) =>
  `rounded-xl border px-4 py-2.5 text-base font-medium ${
    selected ? 'border-brand-600 bg-brand-50 text-brand-700' : 'border-gray-200 bg-white text-gray-600'
  }`;

/** 종류 선택 버튼 (하나만 선택) */
export function TypeButtons({
  value,
  onChange,
}: {
  value: MedicationType | null;
  onChange: (value: MedicationType) => void;
}) {
  return (
    <div role="radiogroup" aria-label="종류" className="flex flex-wrap gap-2">
      {MEDICATION_TYPES.map((t) => (
        <button
          key={t.value}
          type="button"
          role="radio"
          aria-checked={value === t.value}
          onClick={() => onChange(t.value)}
          className={chip(value === t.value)}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}

/** 복용 시간 선택 버튼 (복수 선택) */
export function TimeButtons({
  value,
  onChange,
}: {
  value: MedicationTime[];
  onChange: (value: MedicationTime[]) => void;
}) {
  const toggle = (time: MedicationTime) =>
    onChange(value.includes(time) ? value.filter((t) => t !== time) : [...value, time]);

  return (
    <div role="group" aria-label="복용 시간" className="flex flex-wrap gap-2">
      {MEDICATION_TIMES.map((t) => (
        <button
          key={t.value}
          type="button"
          aria-pressed={value.includes(t.value)}
          onClick={() => toggle(t.value)}
          className={chip(value.includes(t.value))}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}

export const REQUIRED_MESSAGE = '입력해 주세요';
