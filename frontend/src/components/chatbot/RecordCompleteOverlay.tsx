import { useState, type CSSProperties } from 'react';
import defaultImage from '@/assets/atomi/default.png';

interface Props {
  /** 확인 — 오늘 총정리 화면으로 이동한다 */
  onConfirm: () => void;
  /** 다른 기록하기 — 화면을 닫고 대화를 이어 간다 */
  onContinue: () => void;
}

/** 원 게이지 크기. 둘레만큼 선을 숨겨 두었다가 한 바퀴 그린다 */
const RING_SIZE = 144;
const RING_STROKE = 8;
const RING_RADIUS = (RING_SIZE - RING_STROKE) / 2;
const RING_LENGTH = 2 * Math.PI * RING_RADIUS;
/** 사라지는 시간(ms). index.css의 record-complete-out과 맞춘다 */
const LEAVE_MS = 260;

/**
 * 챗봇에서 그날 기록을 처음 다 마쳤을 때 한 번 뜨는 완료 화면.
 * 화면이 부드럽게 나타나고 아토미 둘레의 원 게이지가 한 바퀴 차오르면, 체크 표시와 함께 버튼이 아래에서 올라온다.
 * 버튼을 누르면 화면이 부드럽게 사라진 뒤에 다음 동작을 한다.
 */
export default function RecordCompleteOverlay({ onConfirm, onContinue }: Props) {
  // 게이지가 다 차기 전에는 버튼을 숨겨 둔다. 사라지는 중에는 다시 누를 수 없게 한다.
  const [filled, setFilled] = useState(false);
  const [leaving, setLeaving] = useState(false);

  const leave = (next: () => void) => {
    if (leaving) return;
    setLeaving(true);
    window.setTimeout(next, LEAVE_MS);
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="record-complete-title"
      className={`fixed inset-0 z-50 flex flex-col items-center justify-center bg-[var(--atopass-bg)] px-6 ${
        leaving
          ? 'animate-[record-complete-out_260ms_ease-in_forwards]'
          : 'animate-[record-complete-in_320ms_ease-out_backwards]'
      }`}
    >
      <div className="relative mb-7" style={{ width: RING_SIZE, height: RING_SIZE }}>
        <svg aria-hidden="true" width={RING_SIZE} height={RING_SIZE} className="-rotate-90">
          <circle
            cx={RING_SIZE / 2}
            cy={RING_SIZE / 2}
            r={RING_RADIUS}
            fill="none"
            stroke="var(--color-brand-100)"
            strokeWidth={RING_STROKE}
          />
          <circle
            cx={RING_SIZE / 2}
            cy={RING_SIZE / 2}
            r={RING_RADIUS}
            fill="none"
            stroke="var(--color-brand-600)"
            strokeWidth={RING_STROKE}
            strokeLinecap="round"
            strokeDasharray={RING_LENGTH}
            style={{ '--ring-length': `${RING_LENGTH}px` } as CSSProperties}
            className="animate-[ring-fill_1100ms_cubic-bezier(0.65,0,0.35,1)_250ms_both]"
            onAnimationEnd={() => setFilled(true)}
          />
        </svg>
        <div className="absolute inset-3 flex items-center justify-center rounded-full bg-brand-50">
          <img
            src={defaultImage}
            alt="웃고 있는 아토미"
            className="size-20 animate-[pop-in_420ms_cubic-bezier(0.34,1.56,0.64,1)_150ms_backwards] object-contain"
          />
        </div>
        {filled && (
          <span
            aria-hidden="true"
            className="absolute bottom-1 right-1 flex size-9 animate-[pop-in_360ms_cubic-bezier(0.34,1.56,0.64,1)] items-center justify-center rounded-full bg-brand-600 text-lg font-bold text-white ring-4 ring-[var(--atopass-bg)]"
          >
            ✓
          </span>
        )}
      </div>

      <h2
        id="record-complete-title"
        className="animate-[fade-up_420ms_ease-out_200ms_backwards] text-xl font-bold text-gray-900"
      >
        피부 기록이 완료되었어요!
      </h2>
      <p className="mt-2 animate-[fade-up_420ms_ease-out_320ms_backwards] text-center text-base text-gray-500">
        소중한 기록이
        <br />
        건강한 변화를 만들어가요.
      </p>

      {/* 자리를 미리 잡아 두어, 버튼이 나타날 때 위 내용이 밀리지 않게 한다 */}
      <div
        aria-hidden={!filled}
        className={`mt-8 flex w-full max-w-xs flex-col gap-2.5 transition-all duration-500 ease-out ${
          filled ? 'translate-y-0 opacity-100' : 'pointer-events-none translate-y-4 opacity-0'
        }`}
      >
        <button
          type="button"
          tabIndex={filled ? 0 : -1}
          onClick={() => leave(onConfirm)}
          className="rounded-2xl bg-brand-600 py-3.5 text-lg font-bold text-white transition-transform active:scale-[0.97]"
        >
          확인
        </button>
        <button
          type="button"
          tabIndex={filled ? 0 : -1}
          onClick={() => leave(onContinue)}
          className="rounded-2xl border border-gray-200 bg-white py-3.5 text-lg font-bold text-gray-700 transition-transform active:scale-[0.97]"
        >
          다른 기록하기
        </button>
      </div>
    </div>
  );
}
