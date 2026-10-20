import { CHAT_SLEEP_HOURS_OPTIONS } from '@/utils/constants';

interface Props {
  value: number;
  onChange: (value: number) => void;
  disabled?: boolean;
}

const SLEEP_HOURS_MIN = CHAT_SLEEP_HOURS_OPTIONS[0];
const SLEEP_HOURS_MAX = CHAT_SLEEP_HOURS_OPTIONS[CHAT_SLEEP_HOURS_OPTIONS.length - 1];

/** 수면 시간(0~12시간) 가로 막대. 고통 지수 막대처럼 왼쪽 0시간에서 오른쪽 12시간까지 좌우로 밀어 고른다 */
export default function SleepHoursSlider({ value, onChange, disabled }: Props) {
  return (
    <div className="flex flex-col gap-1">
      <div className="flex items-baseline justify-between">
        <p className="text-sm text-gray-500">어젯밤 잔 시간</p>
        <span className="text-lg font-bold text-emerald-700" aria-hidden>
          {value}시간
        </span>
      </div>
      <input
        type="range"
        min={SLEEP_HOURS_MIN}
        max={SLEEP_HOURS_MAX}
        step={1}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        disabled={disabled}
        aria-label="수면 시간"
        aria-valuetext={`${value}시간`}
        className="h-11 w-full accent-emerald-600"
      />
      <div className="flex justify-between px-1 text-xs text-gray-400" aria-hidden>
        {CHAT_SLEEP_HOURS_OPTIONS.map((h) => (
          <span key={h} className="w-3 text-center">
            {h}
          </span>
        ))}
      </div>
    </div>
  );
}
