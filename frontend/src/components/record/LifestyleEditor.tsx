import type { Lifestyle, OutdoorActivity } from '@/types/record';
import { EMPTY_TEXT, OUTDOOR_ACTIVITY_LABELS, SLEEP_SCORE_MAX } from '@/utils/constants';
import { sleepScore } from '@/utils/sleepScore';

interface Props {
  sleepHours: number | null | undefined;
  lifestyle: Lifestyle | null | undefined;
  onChange: (next: { sleepHours: number | null; lifestyle: Lifestyle | null }) => void;
}

const ACTIVITIES = Object.keys(OUTDOOR_ACTIVITY_LABELS) as OutdoorActivity[];

/** 수면 시간(+수면 점수) + 생활습관(음주, 외출) */
export default function LifestyleEditor({ sleepHours, lifestyle, onChange }: Props) {
  const current = { sleepHours: sleepHours ?? null, lifestyle: lifestyle ?? null };
  const score = sleepScore(current.sleepHours);
  const setLifestyle = (patch: Partial<Lifestyle>) =>
    onChange({ ...current, lifestyle: { ...current.lifestyle, ...patch } });

  return (
    <div className="flex flex-col gap-4">
      <label className="flex items-center justify-between gap-3">
        <span className="text-base text-gray-700">수면 시간</span>
        <span className="flex items-center gap-2">
          <input
            type="number"
            inputMode="decimal"
            min={0}
            max={24}
            step={0.5}
            value={sleepHours ?? ''}
            placeholder={EMPTY_TEXT}
            onChange={(e) => onChange({ ...current, sleepHours: e.target.value === '' ? null : Number(e.target.value) })}
            className="w-28 rounded-lg border border-gray-300 px-3 py-2 text-right text-base"
          />
          시간
        </span>
      </label>
      {score != null && (
        <p className="-mt-2 text-right text-sm text-gray-500">
          수면 점수 {score}/{SLEEP_SCORE_MAX}점 <span className="text-gray-400">(7~9시간 5점, 2시간마다 1점 감점)</span>
        </p>
      )}

      <fieldset className="flex items-center justify-between gap-3">
        <legend className="sr-only">음주</legend>
        <span className="text-base text-gray-700">음주</span>
        <TriState
          value={lifestyle?.alcohol ?? null}
          options={[
            { value: true, label: '했어요' },
            { value: false, label: '안 했어요' },
          ]}
          onChange={(alcohol) => setLifestyle({ alcohol })}
        />
      </fieldset>

      <fieldset className="flex items-center justify-between gap-3">
        <legend className="sr-only">외출</legend>
        <span className="text-base text-gray-700">외출</span>
        <TriState
          value={lifestyle?.outdoorActivity ?? null}
          options={ACTIVITIES.map((a) => ({ value: a, label: OUTDOOR_ACTIVITY_LABELS[a] }))}
          onChange={(outdoorActivity) => setLifestyle({ outdoorActivity })}
        />
      </fieldset>
    </div>
  );
}

/** 선택한 버튼을 다시 누르면 선택 해제(null = 기록 없음) */
function TriState<T extends string | boolean>({
  value,
  options,
  onChange,
}: {
  value: T | null;
  options: { value: T; label: string }[];
  onChange: (value: T | null) => void;
}) {
  return (
    <div className="flex gap-1">
      {options.map((o) => {
        const selected = value === o.value;
        return (
          <button
            key={String(o.value)}
            type="button"
            aria-pressed={selected}
            onClick={() => onChange(selected ? null : o.value)}
            className={`rounded-full border px-3 py-1.5 text-base ${
              selected ? 'border-emerald-600 bg-emerald-50 font-semibold text-emerald-700' : 'border-gray-300 text-gray-600'
            }`}
          >
            {o.label}
          </button>
        );
      })}
    </div>
  );
}
