import { useEffect, useState } from 'react';
import { PAIN_INDEX_MAX, PAIN_INDEX_MIN, PAIN_SCALE } from '@/utils/constants';

interface Props {
  /** null이면 아직 고르지 않음 — 막대는 0에 두고 "기록 없음"으로 보여 준다 */
  value: number | null;
  onChange: (value: number) => void;
  disabled?: boolean;
}

const PAIN_INDEXES = Array.from({ length: PAIN_INDEX_MAX - PAIN_INDEX_MIN + 1 }, (_, i) => PAIN_INDEX_MIN + i);

/**
 * 불편함 지수(0~10) 가로 막대. 왼쪽 0점에서 오른쪽 10점까지 좌우로 밀어 고른다.
 * 막대를 누르고 있는 동안에는 위쪽 안내 대신 지금 점수의 단계와 예시를 보여 준다.
 * 안내 칸의 높이는 그대로라, 누르는 동안 막대가 손가락 아래에서 움직이지 않는다.
 */
export default function PainIndexSlider({ value, onChange, disabled }: Props) {
  const [pressing, setPressing] = useState(false);

  // 손가락이나 마우스를 막대 밖에서 떼도 예시를 닫는다
  useEffect(() => {
    if (!pressing) return;
    const release = () => setPressing(false);
    window.addEventListener('pointerup', release);
    window.addEventListener('pointercancel', release);
    return () => {
      window.removeEventListener('pointerup', release);
      window.removeEventListener('pointercancel', release);
    };
  }, [pressing]);

  const score = value ?? PAIN_INDEX_MIN;
  const scale = PAIN_SCALE[score];

  return (
    <div className="flex flex-col gap-1">
      <div className="h-16">
        {pressing ? (
          <div className="flex h-full flex-col justify-center rounded-xl bg-brand-50 px-3" aria-live="polite">
            <p className="text-sm font-semibold text-brand-700">
              {score}점 · {scale.stage}
            </p>
            <p className="text-sm leading-snug text-gray-700">{scale.example}</p>
          </div>
        ) : (
          <div className="flex items-baseline justify-between">
            <p className="text-sm text-gray-500">불편함 지수 · 0은 없음, 10은 매우 심함</p>
            <span className={value === null ? 'text-base text-gray-400' : 'text-lg font-bold text-brand-700'} aria-hidden>
              {value === null ? '기록 없음' : `${value}점`}
            </span>
          </div>
        )}
      </div>
      <input
        type="range"
        min={PAIN_INDEX_MIN}
        max={PAIN_INDEX_MAX}
        step={1}
        value={score}
        onChange={(e) => onChange(Number(e.target.value))}
        onPointerDown={() => setPressing(true)}
        onBlur={() => setPressing(false)}
        disabled={disabled}
        aria-label="불편함 지수"
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
