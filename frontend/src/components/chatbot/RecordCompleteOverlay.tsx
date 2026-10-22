import defaultImage from '@/assets/atomi/default.png';

interface Props {
  /** 확인 — 오늘 총정리 화면으로 이동한다 */
  onConfirm: () => void;
  /** 다른 기록하기 — 화면을 닫고 대화를 이어 간다 */
  onContinue: () => void;
}

const CONFETTI = [
  { top: '8%', left: '18%', size: 10, color: '#7a9cf6', shape: 'circle', delay: '0ms' },
  { top: '14%', left: '78%', size: 8, color: '#f6b35c', shape: 'rect', delay: '80ms' },
  { top: '30%', left: '10%', size: 7, color: '#5ecf9a', shape: 'circle', delay: '140ms' },
  { top: '26%', left: '86%', size: 9, color: '#f28b82', shape: 'rect', delay: '40ms' },
  { top: '46%', left: '6%', size: 6, color: '#f6b35c', shape: 'circle', delay: '200ms' },
  { top: '42%', left: '92%', size: 7, color: '#7a9cf6', shape: 'circle', delay: '120ms' },
] as const;

/** 챗봇에서 그날 기록을 처음 다 마쳤을 때 한 번 뜨는 완료 축하 화면 */
export default function RecordCompleteOverlay({ onConfirm, onContinue }: Props) {
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="record-complete-title"
      className="fixed inset-0 z-50 flex flex-col items-center justify-center bg-[#f5f7fb] px-6"
    >
      <div className="relative mb-6 flex size-32 items-center justify-center">
        {CONFETTI.map((c, i) => (
          <span
            key={i}
            aria-hidden="true"
            className="absolute animate-[confetti-pop_500ms_ease-out_backwards] motion-reduce:animate-none"
            style={{
              top: c.top,
              left: c.left,
              width: c.size,
              height: c.size,
              backgroundColor: c.color,
              borderRadius: c.shape === 'circle' ? '9999px' : '2px',
              animationDelay: c.delay,
            }}
          />
        ))}
        <div className="flex size-28 items-center justify-center rounded-full bg-brand-100">
          <img src={defaultImage} alt="웃고 있는 아토미" className="size-20 object-contain" />
        </div>
      </div>

      <h2 id="record-complete-title" className="text-xl font-bold text-gray-900">
        피부 기록이 완료되었어요!
      </h2>
      <p className="mt-2 text-center text-base text-gray-500">
        소중한 기록이
        <br />
        건강한 변화를 만들어가요.
      </p>

      <div className="mt-8 flex w-full max-w-xs flex-col gap-2.5">
        <button
          type="button"
          onClick={onConfirm}
          className="rounded-2xl bg-brand-600 py-3.5 text-lg font-bold text-white shadow-[0_8px_20px_rgba(76,116,224,0.28)]"
        >
          확인
        </button>
        <button
          type="button"
          onClick={onContinue}
          className="rounded-2xl border border-gray-200 bg-white py-3.5 text-lg font-bold text-gray-700"
        >
          다른 기록하기
        </button>
      </div>
    </div>
  );
}
