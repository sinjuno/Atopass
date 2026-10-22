import { PAIN_INDEX_MAX, PAIN_INDEX_MIN } from '@/utils/constants';

interface Props {
  /** null이면 아직 고르지 않음 — 막대는 0에 두고 "기록 없음"으로 보여 준다 */
  value: number | null;
  onChange: (value: number) => void;
  disabled?: boolean;
  /** 막대 이름. 가려움 정도처럼 같은 0~10 막대로 받는 항목에 쓴다 */
  label?: string;
}

const PAIN_INDEXES = Array.from({ length: PAIN_INDEX_MAX - PAIN_INDEX_MIN + 1 }, (_, i) => PAIN_INDEX_MIN + i);

/** 고통 지수(0~10) 가로 막대. 왼쪽 0점에서 오른쪽 10점까지 좌우로 밀어 고른다 */
export default function PainIndexSlider({ value, onChange, disabled, label = '고통 지수' }: Props) {
  return (
    <div className="flex flex-col gap-1">
      <div className="flex items-baseline justify-between">
        <p className="text-sm text-gray-500">{label} · 0은 없음, 10은 매우 심함</p>
        <span className={value === null ? 'text-base text-gray-400' : 'text-lg font-bold text-brand-700'} aria-hidden>
          {value === null ? '기록 없음' : `${value}점`}
        </span>
      </div>
      <input
        type="range"
        min={PAIN_INDEX_MIN}
        max={PAIN_INDEX_MAX}
        step={1}
        value={value ?? PAIN_INDEX_MIN}
        onChange={(e) => onChange(Number(e.target.value))}
        disabled={disabled}
        aria-label={label}
        aria-valuetext={value === null ? '기록 없음' : `${value}점`}
        className="h-11 w-full accent-brand-600"
      />
      <div className="flex justify-between px-1 text-xs text-gray-400" aria-hidden>
        {PAIN_INDEXES.map((n) => (
          <span key={n} className="w-3 text-center">
            {n}
          </span>
        ))}
      </div>
    </div>
  );
}
