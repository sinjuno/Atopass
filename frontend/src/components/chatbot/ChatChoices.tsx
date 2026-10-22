import { useEffect, useRef, useState, type AnimationEvent, type ReactNode } from 'react';
import BodyMap from '@/components/record/BodyMap';
import PainIndexSlider from '@/components/record/PainIndexSlider';
import SleepHoursSlider from '@/components/record/SleepHoursSlider';
import type { ChatChoiceRequest, ChoiceField } from '@/types/chatbot';
import type { BodyPart, SkinSign } from '@/types/record';
import { sortSkinSigns, type BodyMapCounts } from '@/utils/bodyMap';
import {
  BODY_MAP_PARTS,
  BODY_PART_LABELS,
  CHAT_SLEEP_HOURS_DEFAULT,
  PAIN_INDEX_MIN,
  PENDING_FIELD_LABELS,
  SKIN_SIGN_DESCRIPTIONS,
  SKIN_SIGN_LABELS,
} from '@/utils/constants';

interface Props {
  field: ChoiceField;
  onChoose: (choice: ChatChoiceRequest) => void;
  disabled?: boolean;
  /** 방금 고른 나타난 증상 — 부위를 고를 때 고른 부위에 이 증상이 들어간다 */
  skinSigns?: SkinSign[];
  /** 선택지를 내려 두면 true. 호출부가 글 입력을 열고 닫는 데 쓴다 */
  onMinimizedChange?: (minimized: boolean) => void;
}

const SKIN_SIGNS = Object.keys(SKIN_SIGN_LABELS) as SkinSign[];

/**
 * 챗봇이 지금 묻는 항목(askingField)의 선택지. 고른 값은 LLM을 거치지 않고 바로 초안에 반영된다.
 * 특이사항은 글로 받으므로 "없어요"만 보여 준다.
 * 고통 지수·증상·부위는 한 묶음이라, 고통 지수 다음에 증상을 고르고, 바로 이어서 마네킹에서 그 증상이 나타난 부위를 고른다.
 * 고른 부위는 증상 개수만큼 붉게 칠해, 저장될 모습을 미리 보여 준다.
 *
 * 질문이 오면 화면 아래에서 올라오고, 답을 고르면 알아서 내려간 뒤 답을 보낸다. 위의 대화는 계속 보인다.
 * 사용자도 손잡이를 아래로 끌거나 눌러 내려 둘 수 있고, 내려 둔 탭을 위로 끌거나 눌러 다시 올릴 수 있다.
 * 다음 질문의 선택지는 호출부가 key를 바꿔 다시 만들어 올라오게 한다.
 */
export default function ChatChoices({ field, onChoose, disabled, skinSigns, onMinimizedChange }: Props) {
  // open: 올라와 있음 · hiding: 사용자가 내리는 중 · minimized: 내려 둠 · closing: 답을 골라 내려가는 중
  const [mode, setMode] = useState<'open' | 'hiding' | 'minimized' | 'closing'>('open');
  const [closed, setClosed] = useState(false);
  const picked = useRef<ChatChoiceRequest | null>(null);

  const pick = (choice: ChatChoiceRequest) => {
    if (mode !== 'open') return;
    picked.current = choice;
    setMode('closing');
  };

  const onAnimationEnd = (e: AnimationEvent<HTMLDivElement>) => {
    if (e.target !== e.currentTarget) return; // 안쪽 요소의 움직임은 무시
    if (mode === 'hiding') setMode('minimized');
    if (mode === 'closing' && picked.current) {
      setClosed(true);
      onChoose(picked.current);
    }
  };

  const minimized = mode === 'minimized';
  useEffect(() => {
    onMinimizedChange?.(minimized);
    return () => onMinimizedChange?.(false);
  }, [minimized, onMinimizedChange]);

  if (closed) return null;

  const label = PENDING_FIELD_LABELS[field];

  return (
    <>
      {/* 내려 둔 상태: 작은 탭만 남는다. 위로 끌거나 누르면 다시 올라온다 */}
      {minimized && (
        <SheetHandle
          direction="up"
          onToggle={() => setMode('open')}
          className="flex shrink-0 animate-[sheet-up_200ms_ease-out] flex-col items-center gap-1 rounded-t-2xl border-t border-gray-200 bg-gray-50 pb-3 pt-2 shadow-[0_-4px_16px_rgba(0,0,0,0.08)]"
        >
          <span className="h-1.5 w-10 rounded-full bg-gray-300" aria-hidden />
          <span className="text-base font-semibold text-brand-700">{label} 고르기 ▲</span>
        </SheetHandle>
      )}

      {/* 마네킹처럼 긴 선택지는 화면 절반까지만 차지하고 안에서 스크롤해, 위의 대화가 가려지지 않게 한다.
          내려 둔 동안에도 고르던 값을 잃지 않도록 숨기기만 한다 (다시 보이면 올라오는 움직임이 다시 나온다) */}
      <div
        onAnimationEnd={onAnimationEnd}
        className={`${field === 'bodyParts' ? 'max-h-[88dvh]' : 'max-h-[50dvh]'} shrink-0 flex-col rounded-t-2xl border-t border-gray-200 bg-gray-50 shadow-[0_-4px_16px_rgba(0,0,0,0.08)] ${
          minimized ? 'hidden' : 'flex'
        } ${
          mode === 'closing'
            ? 'animate-[sheet-down_200ms_ease-in_forwards]'
            : mode === 'hiding'
              ? 'animate-[sheet-down_180ms_ease-in_forwards]'
              : 'animate-[sheet-up_250ms_ease-out]'
        }`}
      >
        <SheetHandle
          direction="down"
          onToggle={() => mode === 'open' && setMode('hiding')}
          className="flex shrink-0 justify-center py-2"
        >
          <span className="h-1.5 w-10 rounded-full bg-gray-300" aria-hidden />
        </SheetHandle>
        <div className="min-h-0 overflow-y-auto px-3 pb-3">
          {/* 항목이 바뀌면 고르던 값을 비우도록 key로 다시 만든다 */}
          <ChoiceBody key={field} field={field} onChoose={pick} disabled={disabled || mode !== 'open'} skinSigns={skinSigns} />
        </div>
      </div>
    </>
  );
}

/** 이만큼(px) 넘게 끌면 내리거나 올린다. 그보다 짧으면 누른 것으로 보고 바로 바꾼다 */
const HANDLE_DRAG = 30;

/**
 * 선택지 패널의 손잡이. direction 쪽으로 끌거나 누르면 onToggle을 부른다 (down: 내리기, up: 올리기).
 * 반대 방향으로 끈 것은 무시한다.
 */
function SheetHandle({
  direction,
  onToggle,
  className,
  children,
}: {
  direction: 'up' | 'down';
  onToggle: () => void;
  className: string;
  children: ReactNode;
}) {
  const startY = useRef<number | null>(null);
  return (
    <div
      role="button"
      tabIndex={0}
      aria-label={direction === 'down' ? '선택지 내리기' : '선택지 올리기'}
      onPointerDown={(e) => {
        startY.current = e.clientY;
      }}
      onPointerUp={(e) => {
        if (startY.current === null) return;
        const moved = e.clientY - startY.current; // 아래로 끌면 양수
        startY.current = null;
        const tapped = Math.abs(moved) < HANDLE_DRAG;
        if (tapped || (direction === 'down' ? moved > 0 : moved < 0)) onToggle();
      }}
      onPointerCancel={() => {
        startY.current = null;
      }}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onToggle();
        }
      }}
      className={`cursor-pointer touch-none select-none ${className}`}
    >
      {children}
    </div>
  );
}

function ChoiceBody({ field, onChoose, disabled, skinSigns = [] }: Props) {
  const [painIndex, setPainIndex] = useState(PAIN_INDEX_MIN);
  const [parts, setParts] = useState<BodyPart[]>([]);
  const [sleepHours, setSleepHours] = useState(CHAT_SLEEP_HOURS_DEFAULT);
  const [signs, setSigns] = useState<SkinSign[]>([]);

  const skip = field !== 'painIndex' && field !== 'bodyParts' && field !== 'notes' && field !== 'signImage' && (
    <ChoiceButton onClick={() => onChoose({ field, skip: true })} disabled={disabled} subtle>
      넘어가기
    </ChoiceButton>
  );

  switch (field) {
    case 'painIndex':
      return (
        <div className="flex items-end gap-3">
          <div className="flex-1">
            <PainIndexSlider value={painIndex} onChange={setPainIndex} disabled={disabled} />
          </div>
          <ChoiceButton onClick={() => onChoose({ field, value: painIndex })} disabled={disabled} primary>
            확인
          </ChoiceButton>
        </div>
      );
    case 'bodyParts': {
      // 고른 부위에는 방금 고른 증상이 들어가므로, 그 개수만큼 붉게 칠한다 (증상이 없으면 하얀색에 진한 테두리)
      const counts: BodyMapCounts = Object.fromEntries(parts.map((part) => [part, skinSigns.length]));
      const toggle = (part: BodyPart) =>
        setParts((prev) =>
          prev.includes(part) ? prev.filter((p) => p !== part) : BODY_MAP_PARTS.filter((p) => p === part || prev.includes(p)),
        );
      // 핸드폰에서도 누르기 쉽도록 마네킹을 카드에 크게 띄우고, 고른 부위와 버튼은 그 아래에 둔다
      return (
        <div className="flex flex-col gap-3">
          <p className="text-center text-base font-semibold text-gray-800">
            {skinSigns.length > 0
              ? `${sortSkinSigns(skinSigns)
                  .map((sign) => SKIN_SIGN_LABELS[sign])
                  .join(', ')} — 나타난 부위를 모두 눌러 주세요`
              : '고통이 있는 부위를 모두 눌러 주세요'}
          </p>
          <div className="rounded-2xl bg-white p-4 shadow-card">
            <BodyMap counts={counts} selected={parts} onSelect={toggle} maxWidth={170} />
          </div>
          <p className="min-h-6 text-center text-base text-gray-700">
            {parts.length > 0 ? parts.map((part) => BODY_PART_LABELS[part]).join(', ') : '아직 고른 부위가 없어요'}
          </p>
          <div className="flex gap-2">
            <ChoiceButton onClick={() => onChoose({ field, skip: true })} disabled={disabled}>
              없어요
            </ChoiceButton>
            <ChoiceButton onClick={() => onChoose({ field, value: parts })} disabled={disabled || parts.length === 0} primary wide>
              {parts.length > 0 ? `${parts.length}곳 선택 완료` : '선택 완료'}
            </ChoiceButton>
          </div>
        </div>
      );
    }
    case 'sleepHours':
      return (
        <div className="flex flex-col gap-2">
          <SleepHoursSlider value={sleepHours} onChange={setSleepHours} disabled={disabled} />
          <div className="flex gap-2">
            <ChoiceButton onClick={() => onChoose({ field, value: sleepHours })} disabled={disabled} primary wide>
              확인
            </ChoiceButton>
            {skip}
          </div>
        </div>
      );
    case 'medication':
      return (
        <div className="flex gap-2" role="group" aria-label="약 복용">
          <ChoiceButton onClick={() => onChoose({ field, value: true })} disabled={disabled} wide>
            예
          </ChoiceButton>
          <ChoiceButton onClick={() => onChoose({ field, value: false })} disabled={disabled} wide>
            아니오
          </ChoiceButton>
          {skip}
        </div>
      );
    case 'skinSigns': {
      const toggle = (sign: SkinSign) =>
        setSigns((prev) =>
          prev.includes(sign) ? prev.filter((s) => s !== sign) : SKIN_SIGNS.filter((s) => s === sign || prev.includes(s)),
        );
      return (
        <div className="flex flex-col gap-2">
          <div className="grid grid-cols-2 gap-2" role="group" aria-label="나타난 증상">
            {SKIN_SIGNS.map((sign) => {
              const selected = signs.includes(sign);
              return (
                <button
                  key={sign}
                  type="button"
                  aria-pressed={selected}
                  onClick={() => toggle(sign)}
                  disabled={disabled}
                  className={`flex min-h-11 flex-col items-start gap-0.5 rounded-xl border px-3 py-2 text-left ${
                    selected ? 'border-brand-600 bg-brand-50 text-brand-700' : 'border-gray-300 bg-white text-gray-700'
                  }`}
                >
                  <span className={`text-base ${selected ? 'font-semibold' : ''}`}>{SKIN_SIGN_LABELS[sign]}</span>
                  <span className={`text-sm leading-snug ${selected ? 'text-brand-700' : 'text-gray-500'}`}>
                    {SKIN_SIGN_DESCRIPTIONS[sign]}
                  </span>
                </button>
              );
            })}
          </div>
          <div className="flex gap-2">
            <ChoiceButton onClick={() => onChoose({ field, value: signs })} disabled={disabled || signs.length === 0} primary wide>
              {signs.length > 0 ? `${signs.length}개 선택 완료` : '선택 완료'}
            </ChoiceButton>
            <ChoiceButton onClick={() => onChoose({ field, value: [] })} disabled={disabled}>
              없어요
            </ChoiceButton>
            {skip}
          </div>
        </div>
      );
    }
    case 'notes':
      return (
        <div className="flex items-center justify-between gap-2">
          <span className="text-sm text-gray-500">특이사항은 아래에 적어 주세요</span>
          <ChoiceButton onClick={() => onChoose({ field, skip: true })} disabled={disabled}>
            없어요
          </ChoiceButton>
        </div>
      );
    case 'signImage':
      // 증상을 물었을 때, 예시 사진(실제 피부 사진)을 보여 줘도 되는지에 대한 답. 보여 달라고 할 때만 사진을 불러온다
      return (
        <div className="flex gap-2" role="group" aria-label="증상 예시 사진">
          <ChoiceButton onClick={() => onChoose({ field, value: true })} disabled={disabled} primary wide>
            보여 주세요
          </ChoiceButton>
          <ChoiceButton onClick={() => onChoose({ field, value: false })} disabled={disabled} wide>
            괜찮아요
          </ChoiceButton>
        </div>
      );
  }
}

function ChoiceButton({
  children,
  onClick,
  disabled,
  primary,
  subtle,
  wide,
}: {
  children: ReactNode;
  onClick: () => void;
  disabled?: boolean;
  primary?: boolean;
  subtle?: boolean;
  wide?: boolean;
}) {
  const tone = primary
    ? 'border-brand-600 bg-brand-600 font-semibold text-white'
    : subtle
      ? 'border-gray-300 bg-white text-gray-500'
      : 'border-gray-300 bg-white text-gray-800';
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={`min-h-11 shrink-0 rounded-full border-2 px-5 text-base disabled:opacity-40 ${tone} ${wide ? 'flex-1' : ''}`}
    >
      {children}
    </button>
  );
}
