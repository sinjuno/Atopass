import { useEffect, useRef, useState, type AnimationEvent, type ReactNode } from 'react';
import BodyMap from '@/components/record/BodyMap';
import PainIndexSlider from '@/components/record/PainIndexSlider';
import SleepHoursSlider from '@/components/record/SleepHoursSlider';
import type {
  ChatChoiceRequest,
  ChoiceField,
  LongGapAnswer,
  MissedMedicationReason,
  ShortSleepReason,
} from '@/types/chatbot';
import type { BodyPart, SkinSign } from '@/types/record';
import { sortSkinSigns, type BodyMapCounts } from '@/utils/bodyMap';
import {
  BODY_MAP_PARTS,
  BODY_PART_LABELS,
  CHAT_SLEEP_HOURS_DEFAULT,
  LONG_GAP_ANSWER_LABELS,
  MISSED_MEDICATION_REASON_LABELS,
  PAIN_INDEX_MIN,
  PENDING_FIELD_LABELS,
  PHOTO_ACCEPT,
  SHORT_SLEEP_REASON_LABELS,
  SITUATIONAL_TEXT_MAX_LENGTH,
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
  /** 상황 질문(P01)의 "사진도 남기기" 버튼 — ChatInput의 첨부 버튼과 같은 곳에 쌓인다 */
  onAttachPhotos?: (files: File[]) => void;
}

const SKIN_SIGNS = Object.keys(SKIN_SIGN_LABELS) as SkinSign[];

/**
 * 챗봇이 지금 묻는 항목(askingField)의 선택지. 고른 값은 LLM을 거치지 않고 바로 초안에 반영된다.
 * 특이사항은 글로 받으므로 "없어요"만 보여 준다.
 * 불편함 지수·증상·부위는 한 묶음이라, 불편함 지수 다음에 증상을 고르고, 바로 이어서 마네킹에서 그 증상이 나타난 부위를 고른다.
 * 고른 부위는 증상 개수만큼 붉게 칠해, 저장될 모습을 미리 보여 준다.
 *
 * 질문이 오면 화면 아래에서 올라오고, 답을 고르면 알아서 내려간 뒤 답을 보낸다. 위의 대화는 계속 보인다.
 * 사용자도 손잡이를 아래로 끌거나 눌러 내려 둘 수 있고, 내려 둔 탭을 위로 끌거나 눌러 다시 올릴 수 있다.
 * 다음 질문의 선택지는 호출부가 key를 바꿔 다시 만들어 올라오게 한다.
 */
export default function ChatChoices({ field, onChoose, disabled, skinSigns, onMinimizedChange, onAttachPhotos }: Props) {
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
        className={`${field === 'bodyParts' ? 'max-h-[88dvh] min-h-0' : 'max-h-[50dvh] shrink-0'} flex-col rounded-t-2xl border-t border-gray-200 bg-gray-50 shadow-[0_-4px_16px_rgba(0,0,0,0.08)] ${
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
          <ChoiceBody
            key={field}
            field={field}
            onChoose={pick}
            disabled={disabled || mode !== 'open'}
            skinSigns={skinSigns}
            onAttachPhotos={onAttachPhotos}
          />
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

function ChoiceBody({ field, onChoose, disabled, skinSigns = [], onAttachPhotos }: Props) {
  const [painIndex, setPainIndex] = useState(PAIN_INDEX_MIN);
  const [parts, setParts] = useState<BodyPart[]>([]);
  const [sleepHours, setSleepHours] = useState(CHAT_SLEEP_HOURS_DEFAULT);
  const [signs, setSigns] = useState<SkinSign[]>([]);
  const [c02, setC02] = useState<{ productNote?: string; unusualNote?: string; clinicQuestion?: string }>({});
  const photoRef = useRef<HTMLInputElement>(null);

  const skip = field !== 'painIndex' && field !== 'bodyParts' && field !== 'notes' && field !== 'signImage'
    && field !== 'C02' && (
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
            <BodyMap counts={counts} selected={parts} onSelect={toggle} maxWidth={170} maxHeight="30dvh" />
          </div>
          <p className="min-h-6 text-center text-base text-gray-700">
            {parts.length > 0 ? parts.map((part) => BODY_PART_LABELS[part]).join(', ') : '아직 고른 부위가 없어요'}
          </p>
          {/* 화면이 낮아 내용이 스크롤되어도 버튼은 항상 보이게 아래에 붙여 둔다 */}
          <div className="sticky bottom-0 -mb-3 flex gap-2 bg-gray-50 pb-3 pt-1">
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
    case 'P01':
      // 불편함 지수가 어제보다 많이 오른 날만 나오는 상황 질문. 사진도 바로 붙일 수 있게 둔다.
      return (
        <div className="flex flex-col gap-2">
          <div className="flex gap-2" role="group" aria-label="불편함 지수가 언제부터 심해졌는지">
            <ChoiceButton onClick={() => onChoose({ field, value: 'TODAY' })} disabled={disabled} wide>
              오늘
            </ChoiceButton>
            <ChoiceButton onClick={() => onChoose({ field, value: 'SINCE_YESTERDAY' })} disabled={disabled} wide>
              어제부터
            </ChoiceButton>
            <ChoiceButton onClick={() => onChoose({ field, value: 'FEW_DAYS_AGO' })} disabled={disabled} wide>
              2~3일 전
            </ChoiceButton>
          </div>
          <div className="flex gap-2">
            <ChoiceButton onClick={() => photoRef.current?.click()} disabled={disabled || !onAttachPhotos} subtle wide>
              사진도 남기기
            </ChoiceButton>
            {skip}
          </div>
          <input
            ref={photoRef}
            type="file"
            accept={PHOTO_ACCEPT.join(',')}
            multiple
            hidden
            onChange={(e) => {
              onAttachPhotos?.(Array.from(e.target.files ?? []));
              e.target.value = '';
            }}
          />
        </div>
      );
    case 'P02':
      // 어제 없던 증상·부위가 생긴 날만 나오는 상황 질문. 질문 문구(증상 이름)는 서버가 정한다.
      return (
        <SituationalTextAnswer
          disabled={disabled}
          placeholder="예: 새 바디워시를 쓰고 나서 생겼어요"
          template="하고 나서 생겼어요"
          templateLabel="~하고 나서 생겼어요"
          unknownLabel="잘 모르겠어요"
          ariaLabel="어떻게 시작됐는지"
          onSubmit={(text) => onChoose({ field, value: text })}
          skip={skip}
        />
      );
    case 'P03':
      // 불편함 지수가 높은 날이 며칠 이어질 때만 나오는 상황 질문. 칩은 "요즘 " 뒤에 커서를 둔다.
      return (
        <SituationalTextAnswer
          disabled={disabled}
          placeholder="예: 요즘 야근이 많아 늦게 자요"
          template="요즘 해요"
          templateLabel="요즘 ~해요"
          templateCursor={3}
          unknownLabel="특별히 없어요"
          ariaLabel="요즘 생활에서 달라진 것"
          onSubmit={(text) => onChoose({ field, value: text })}
          skip={skip}
        />
      );
    case 'P06':
      // 직전 기록이 며칠 전일 때만 나오는 상황 질문. 하나 고르고, 덧붙일 말은 적어도 되고 안 적어도 된다.
      return <LongGapChoices disabled={disabled} onPick={(value) => onChoose({ field, value })} skip={skip} />;
    case 'P07':
      // 불편함 지수가 어제보다 많이 내린 날만 나오는 상황 질문
      return (
        <SituationalTextAnswer
          disabled={disabled}
          placeholder="예: 보습제를 바꿨더니 나아졌어요"
          template="했더니 나아졌어요"
          templateLabel="~했더니 나아졌어요"
          unknownLabel="잘 모르겠어요"
          ariaLabel="좋아지기 전에 달라진 것"
          onSubmit={(text) => onChoose({ field, value: text })}
          skip={skip}
        />
      );
    case 'P04':
      // 잠을 적게 자고 불편함 지수도 높은 날만 나오는 상황 질문
      return (
        <ReasonChoices
          labels={SHORT_SLEEP_REASON_LABELS}
          ariaLabel="잠을 적게 잔 이유"
          disabled={disabled}
          onPick={(value: ShortSleepReason) => onChoose({ field, value })}
          skip={skip}
        />
      );
    case 'P05':
      // 평소 약을 챙기다 오늘 못 챙긴 날만 나오는 상황 질문. 이유만 남기고 복용을 권하지 않는다.
      return (
        <ReasonChoices
          labels={MISSED_MEDICATION_REASON_LABELS}
          ariaLabel="약을 못 챙긴 이유"
          disabled={disabled}
          onPick={(value: MissedMedicationReason) => onChoose({ field, value })}
          skip={skip}
        />
      );
    case 'C02': {
      // 매일 특이사항 다음에 묻는 마무리 질문. 고른 항목만 보내고, "없어요"는 아무것도 저장하지 않는다.
      const items: Array<{
        key: 'productNote' | 'unusualNote' | 'clinicQuestion';
        label: string;
        placeholder: string;
      }> = [
        { key: 'productNote', label: '새로 쓴 제품', placeholder: '예: 새 보습제' },
        { key: 'unusualNote', label: '평소와 다른 일', placeholder: '예: 늦게 잤어요' },
        { key: 'clinicQuestion', label: '진료 때 물어볼 것', placeholder: '예: 이 연고 계속 써도 될까요?' },
      ];
      const toggle = (key: 'productNote' | 'unusualNote' | 'clinicQuestion') =>
        setC02((prev) => {
          const next = { ...prev };
          if (key in next) delete next[key];
          else next[key] = '';
          return next;
        });
      const hasAny = Object.keys(c02).length > 0;
      return (
        <div className="flex flex-col gap-2">
          {items.map(({ key, label, placeholder }) => {
            const checked = key in c02;
            return (
              <label key={key} className="flex flex-col gap-1 rounded-xl border border-gray-300 bg-white px-3 py-2">
                <span className="flex items-center gap-2">
                  <input type="checkbox" checked={checked} onChange={() => toggle(key)} disabled={disabled} />
                  <span className="text-base text-gray-800">{label}</span>
                </span>
                {checked && (
                  <input
                    type="text"
                    value={c02[key] ?? ''}
                    onChange={(e) => setC02((prev) => ({ ...prev, [key]: e.target.value }))}
                    placeholder={placeholder}
                    disabled={disabled}
                    className="rounded-lg border border-gray-200 px-2 py-1 text-base"
                  />
                )}
              </label>
            );
          })}
          <div className="flex gap-2">
            <ChoiceButton onClick={() => onChoose({ field, value: c02 })} disabled={disabled || !hasAny} primary wide>
              적어 둘게요
            </ChoiceButton>
            <ChoiceButton onClick={() => onChoose({ field, skip: true })} disabled={disabled}>
              없어요
            </ChoiceButton>
          </div>
        </div>
      );
    }
  }
}

/** 이유를 하나 고르는 상황 질문(P04·P05). 버튼 2개씩 두 줄로 두고, 맨 아래에 넘어가기 */
function ReasonChoices<T extends string>({
  labels,
  ariaLabel,
  disabled,
  onPick,
  skip,
}: {
  labels: Record<T, string>;
  ariaLabel: string;
  disabled?: boolean;
  onPick: (value: T) => void;
  skip: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-2">
      <div className="grid grid-cols-2 gap-2" role="group" aria-label={ariaLabel}>
        {(Object.keys(labels) as T[]).map((value) => (
          <ChoiceButton key={value} onClick={() => onPick(value)} disabled={disabled}>
            {labels[value]}
          </ChoiceButton>
        ))}
      </div>
      <div className="flex justify-end">{skip}</div>
    </div>
  );
}

/**
 * 며칠 만에 기록할 때 그동안 피부가 어땠는지 묻는 상황 질문(P06). 하나 고르면 덧붙일 말을 적는 칸이 열리고,
 * 적지 않아도 "적어 둘게요"로 보낼 수 있다.
 */
function LongGapChoices({
  disabled,
  onPick,
  skip,
}: {
  disabled?: boolean;
  onPick: (value: { answer: LongGapAnswer; text?: string }) => void;
  skip: ReactNode;
}) {
  const [answer, setAnswer] = useState<LongGapAnswer | null>(null);
  const [text, setText] = useState('');
  const trimmed = text.trim();

  return (
    <div className="flex flex-col gap-2">
      <div className="flex gap-2" role="group" aria-label="그동안 피부는 어땠는지">
        {(Object.keys(LONG_GAP_ANSWER_LABELS) as LongGapAnswer[]).map((value) => {
          const selected = answer === value;
          return (
            <button
              key={value}
              type="button"
              aria-pressed={selected}
              onClick={() => setAnswer(value)}
              disabled={disabled}
              className={`min-h-11 flex-1 rounded-full border-2 px-3 text-base disabled:opacity-40 ${
                selected ? 'border-brand-600 bg-brand-50 font-semibold text-brand-700' : 'border-gray-300 bg-white text-gray-800'
              }`}
            >
              {LONG_GAP_ANSWER_LABELS[value]}
            </button>
          );
        })}
      </div>
      {answer && (
        <>
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            maxLength={SITUATIONAL_TEXT_MAX_LENGTH}
            rows={2}
            placeholder="(선택) 예: 연고를 바꿨어요"
            disabled={disabled}
            aria-label="덧붙일 말"
            className="resize-none rounded-xl border border-gray-300 bg-white px-3 py-2 text-base"
          />
          <p className="text-right text-sm text-gray-500">
            {text.length}/{SITUATIONAL_TEXT_MAX_LENGTH}
          </p>
        </>
      )}
      <div className="flex gap-2">
        <ChoiceButton
          onClick={() => answer && onPick(trimmed ? { answer, text: trimmed } : { answer })}
          disabled={disabled || !answer}
          primary
          wide
        >
          적어 둘게요
        </ChoiceButton>
        {skip}
      </div>
    </div>
  );
}

/**
 * 글로 답하는 상황 질문(P02·P03·P07). 시작 칩을 누르면 문장 일부가 채워지고 커서가 templateCursor(기본은 맨 앞)에 와서
 * 빈 곳만 적으면 된다. "잘 모르겠어요" 같은 칩은 그 말로 바로 저장한다.
 */
function SituationalTextAnswer({
  disabled,
  placeholder,
  template,
  templateLabel,
  templateCursor = 0,
  unknownLabel,
  ariaLabel,
  onSubmit,
  skip,
}: {
  disabled?: boolean;
  placeholder: string;
  template: string;
  templateLabel: string;
  templateCursor?: number;
  unknownLabel: string;
  ariaLabel: string;
  onSubmit: (text: string) => void;
  skip: ReactNode;
}) {
  const [text, setText] = useState('');
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const trimmed = text.trim();

  const fillTemplate = () => {
    setText(template);
    // 값이 그려진 뒤에 커서를 빈 곳으로 옮긴다
    requestAnimationFrame(() => {
      inputRef.current?.focus();
      inputRef.current?.setSelectionRange(templateCursor, templateCursor);
    });
  };

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap gap-2">
        <ChoiceButton onClick={fillTemplate} disabled={disabled}>
          {templateLabel}
        </ChoiceButton>
        <ChoiceButton onClick={() => onSubmit(unknownLabel)} disabled={disabled}>
          {unknownLabel}
        </ChoiceButton>
      </div>
      <textarea
        ref={inputRef}
        value={text}
        onChange={(e) => setText(e.target.value)}
        maxLength={SITUATIONAL_TEXT_MAX_LENGTH}
        rows={2}
        placeholder={placeholder}
        disabled={disabled}
        aria-label={ariaLabel}
        className="resize-none rounded-xl border border-gray-300 bg-white px-3 py-2 text-base"
      />
      <p className="text-right text-sm text-gray-500">
        {text.length}/{SITUATIONAL_TEXT_MAX_LENGTH}
      </p>
      <div className="flex gap-2">
        <ChoiceButton onClick={() => onSubmit(trimmed)} disabled={disabled || trimmed.length === 0} primary wide>
          적어 둘게요
        </ChoiceButton>
        {skip}
      </div>
    </div>
  );
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
