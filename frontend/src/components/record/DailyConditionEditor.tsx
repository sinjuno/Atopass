import type { SkinSign } from '@/types/record';
import { EMPTY_TEXT, PAIN_INDEX_MAX, PAIN_INDEX_MIN, SKIN_SIGN_LABELS } from '@/utils/constants';

interface Value {
  painIndex: number | null;
  medication: boolean | null;
  skinSigns: SkinSign[];
}

interface Props {
  painIndex: number | null | undefined;
  medication: boolean | null | undefined;
  skinSigns: SkinSign[] | null | undefined;
  onChange: (next: Value) => void;
}

const PAIN_INDEXES = Array.from({ length: PAIN_INDEX_MAX - PAIN_INDEX_MIN + 1 }, (_, i) => PAIN_INDEX_MIN + i);
const SKIN_SIGNS = Object.keys(SKIN_SIGN_LABELS) as SkinSign[];

/** 고통 지수(1~10) + 약 복용 여부(예/아니오) + 나타난 증상 7가지(여러 개 선택) */
export default function DailyConditionEditor({ painIndex, medication, skinSigns, onChange }: Props) {
  const current: Value = { painIndex: painIndex ?? null, medication: medication ?? null, skinSigns: skinSigns ?? [] };

  const toggleSign = (sign: SkinSign) => {
    const next = current.skinSigns.includes(sign)
      ? current.skinSigns.filter((s) => s !== sign)
      : SKIN_SIGNS.filter((s) => s === sign || current.skinSigns.includes(s));
    onChange({ ...current, skinSigns: next });
  };

  return (
    <div className="flex flex-col gap-5">
      <label className="flex items-center justify-between gap-3">
        <span className="text-base text-gray-700">고통 지수</span>
        <select
          value={current.painIndex ?? ''}
          onChange={(e) => onChange({ ...current, painIndex: e.target.value === '' ? null : Number(e.target.value) })}
          className="rounded-lg border border-gray-300 px-3 py-2 text-base"
        >
          <option value="">{EMPTY_TEXT}</option>
          {PAIN_INDEXES.map((n) => (
            <option key={n} value={n}>
              {n}점
            </option>
          ))}
        </select>
      </label>

      <fieldset className="flex items-center justify-between gap-3">
        <legend className="sr-only">약 복용 여부</legend>
        <span className="text-base text-gray-700">약 복용</span>
        <div className="flex gap-1">
          {[
            { value: true, label: '예' },
            { value: false, label: '아니오' },
          ].map((o) => {
            const selected = current.medication === o.value;
            return (
              <button
                key={o.label}
                type="button"
                aria-pressed={selected}
                // 선택한 버튼을 다시 누르면 선택 해제(null = 기록 없음)
                onClick={() => onChange({ ...current, medication: selected ? null : o.value })}
                className={`rounded-full border px-4 py-1.5 text-base ${
                  selected ? 'border-emerald-600 bg-emerald-50 font-semibold text-emerald-700' : 'border-gray-300 text-gray-600'
                }`}
              >
                {o.label}
              </button>
            );
          })}
        </div>
      </fieldset>

      <fieldset>
        <legend className="mb-2 text-base text-gray-700">나타난 증상</legend>
        <div className="grid grid-cols-2 gap-2">
          {SKIN_SIGNS.map((sign, i) => {
            const checked = current.skinSigns.includes(sign);
            return (
              <label
                key={sign}
                className={`flex cursor-pointer items-center gap-2 rounded-xl border px-3 py-2 text-base ${
                  checked ? 'border-emerald-600 bg-emerald-50 text-emerald-700' : 'border-gray-300 text-gray-700'
                }`}
              >
                <input type="checkbox" checked={checked} onChange={() => toggleSign(sign)} className="size-4" />
                {i + 1}. {SKIN_SIGN_LABELS[sign]}
              </label>
            );
          })}
        </div>
      </fieldset>
    </div>
  );
}
