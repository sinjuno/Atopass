import { useState, type KeyboardEvent, type ReactNode } from 'react';
import type { BodyPart } from '@/types/record';
import { BODY_MAP_EMPTY_COLOR, SIGN_COUNT_MAX, signCountColor, type BodyMapCounts } from '@/utils/bodyMap';
import { BODY_PART_LABELS } from '@/utils/constants';

interface Props {
  /** 기록한 부위마다 나타난 증상 개수 — 많을수록 붉게 칠한다 */
  counts: BodyMapCounts;
  /** 고른 부위 — 진한 테두리로 표시. 여러 곳을 고를 때는 배열로 넘긴다 */
  selected?: BodyPart | readonly BodyPart[] | null;
  /** 부위를 눌렀을 때 */
  onSelect: (part: BodyPart) => void;
  /** 마네킹 가로 최대 크기(px). 기본 190 */
  maxWidth?: number;
}

/**
 * 부위마다 그릴 모양 — 한 사람 그림은 가로 200, 세로 400.
 * 앞면은 마주 보는 그림이라 사람의 오른쪽이 화면 왼쪽에 온다. 뒷면은 좌우를 뒤집어 그린다.
 */
const TRUNK_SHAPE = (
  <path d="M64 90 Q100 82 136 90 Q146 94 145 106 L136 160 Q134 186 136 214 L64 214 Q66 186 64 160 L55 106 Q54 94 64 90 Z" />
);
const GROIN_SHAPE = <path d="M64 212 L136 212 Q138 232 126 242 Q112 252 100 252 Q88 252 74 242 Q62 232 64 212 Z" />;
const HEAD_SHAPE = <ellipse cx="100" cy="46" rx="21" ry="26" />;

const SHAPES: Partial<Record<BodyPart, ReactNode>> = {
  FACE: HEAD_SHAPE,
  NECK: <rect x="89" y="68" width="22" height="22" rx="6" />,
  RIGHT_UPPER_ARM: <rect x="-9" y="0" width="18" height="72" rx="9" transform="translate(56 94) rotate(13)" />,
  LEFT_UPPER_ARM: <rect x="-9" y="0" width="18" height="72" rx="9" transform="translate(144 94) rotate(-13)" />,
  RIGHT_FOREARM: <rect x="-8" y="0" width="16" height="66" rx="8" transform="translate(40 160) rotate(7)" />,
  LEFT_FOREARM: <rect x="-8" y="0" width="16" height="66" rx="8" transform="translate(160 160) rotate(-7)" />,
  RIGHT_HAND: <ellipse cx="31" cy="237" rx="10" ry="13" />,
  LEFT_HAND: <ellipse cx="169" cy="237" rx="10" ry="13" />,
  RIGHT_THIGH: <rect x="-14" y="0" width="28" height="86" rx="13" transform="translate(85 222) rotate(3)" />,
  LEFT_THIGH: <rect x="-14" y="0" width="28" height="86" rx="13" transform="translate(115 222) rotate(-3)" />,
  RIGHT_LOWER_LEG: <rect x="-10" y="0" width="20" height="78" rx="10" transform="translate(82 304) rotate(1)" />,
  LEFT_LOWER_LEG: <rect x="-10" y="0" width="20" height="78" rx="10" transform="translate(118 304) rotate(-1)" />,
  RIGHT_FOOT: <ellipse cx="79" cy="388" rx="14" ry="8" />,
  LEFT_FOOT: <ellipse cx="121" cy="388" rx="14" ry="8" />,
  TRUNK: TRUNK_SHAPE,
  BACK: TRUNK_SHAPE,
  GROIN: GROIN_SHAPE,
};

/** 앞·뒷면 모두에 있는 팔·다리·손·발. 뒤에 그린 부위가 겹친 곳을 덮는다(하박 위에 손, 하박 위에 발) */
const LIMBS: BodyPart[] = [
  'RIGHT_UPPER_ARM',
  'LEFT_UPPER_ARM',
  'RIGHT_FOREARM',
  'LEFT_FOREARM',
  'RIGHT_HAND',
  'LEFT_HAND',
  'RIGHT_THIGH',
  'LEFT_THIGH',
  'RIGHT_LOWER_LEG',
  'LEFT_LOWER_LEG',
  'RIGHT_FOOT',
  'LEFT_FOOT',
];

/** 그리는 순서 — 팔 위에 몸, 허벅지 위에 사타구니. 뒷면은 이 위에 엉덩이·목·뒤통수를 그린다 */
const FRONT_ORDER: BodyPart[] = [...LIMBS, 'TRUNK', 'GROIN', 'NECK', 'FACE'];
const BACK_ORDER: BodyPart[] = [...LIMBS, 'BACK'];

type Side = 'front' | 'back';
const SIDE_LABELS: Record<Side, string> = { front: '앞', back: '뒤' };

/** 뒷면은 앞면 그림을 좌우로 뒤집어 그린다 — 그림 가로 200 */
const BACK_TRANSFORM = 'translate(200 0) scale(-1 1)';
const DECORATION = { fill: '#f3f4f6', stroke: '#d1d5db', strokeWidth: 1.5 };

/**
 * 사람 모양 마네킹. 앞·뒤 버튼으로 앞모습과 뒷모습을 바꿔 본다.
 * 부위 색은 나타난 증상이 많을수록 붉다(0개는 하얀색, 7개는 새빨간색, 기록 없음은 회색).
 * 부위를 누르면 고른다. 팔·다리·손·발·목은 앞뒤 어느 쪽을 눌러도 같은 부위다.
 */
export default function BodyMap({ counts, selected, onSelect, maxWidth = 190 }: Props) {
  const [side, setSide] = useState<Side>('front');
  const selectedParts: readonly BodyPart[] = selected == null ? [] : typeof selected === 'string' ? [selected] : selected;
  const renderPart = (part: BodyPart, side: 'front' | 'back') => {
    const count = counts[part];
    const label = `${BODY_PART_LABELS[part]} ${count === undefined ? '기록 없음' : `증상 ${count}개`}`;
    const isSelected = selectedParts.includes(part);
    const select = () => onSelect(part);
    return (
      <g
        key={`${side}-${part}`}
        role="button"
        tabIndex={0}
        aria-label={label}
        aria-pressed={isSelected}
        onClick={select}
        onKeyDown={(e: KeyboardEvent) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            select();
          }
        }}
        fill={signCountColor(count)}
        // 기록한 부위는 하얀색(증상 0개)이어도 보이도록 회색 테두리를 두른다
        stroke={isSelected ? '#111827' : count === undefined ? '#ffffff' : '#9ca3af'}
        strokeWidth={isSelected ? 3 : 1.5}
        className="cursor-pointer outline-none transition-[fill] hover:opacity-85 focus-visible:opacity-75"
      >
        <title>{label}</title>
        {SHAPES[part]}
      </g>
    );
  };

  return (
    <div className="flex flex-col items-center gap-3">
      <div className="flex rounded-full bg-gray-100 p-1" role="group" aria-label="앞모습·뒷모습">
        {(Object.keys(SIDE_LABELS) as Side[]).map((s) => (
          <button
            key={s}
            type="button"
            aria-pressed={side === s}
            onClick={() => setSide(s)}
            className={`min-h-9 rounded-full px-4 text-base ${
              side === s ? 'bg-white font-semibold text-gray-900 shadow-sm' : 'text-gray-500'
            }`}
          >
            {SIDE_LABELS[s]}
          </button>
        ))}
      </div>

      <svg
        viewBox="0 0 200 400"
        className="h-auto w-full"
        style={{ maxWidth }}
        role="group"
        aria-label={`부위별 증상 마네킹 ${SIDE_LABELS[side]}모습`}
      >
        {/* 앞모습은 마주 보는 그림이라 사람의 오른쪽이 화면 왼쪽에, 뒷모습은 그 반대다 */}
        <g fill="#9ca3af" fontSize="11" textAnchor="middle">
          <text x="24" y="14">{side === 'front' ? '오른쪽' : '왼쪽'}</text>
          <text x="176" y="14">{side === 'front' ? '왼쪽' : '오른쪽'}</text>
        </g>

        {side === 'front' ? (
          <g>{FRONT_ORDER.map((part) => renderPart(part, 'front'))}</g>
        ) : (
          <g transform={BACK_TRANSFORM}>
            {BACK_ORDER.map((part) => renderPart(part, 'back'))}
            {/* 엉덩이·뒤통수는 고르는 부위가 아니라 모양만 그린다 */}
            <g {...DECORATION}>{GROIN_SHAPE}</g>
            {renderPart('NECK', 'back')}
            <g {...DECORATION}>{HEAD_SHAPE}</g>
          </g>
        )}
      </svg>
    </div>
  );
}

/** 색 범례: 기록 없음(회색) / 증상 0개(하얀색) → 7개(새빨간색) */
export function BodyMapLegend() {
  return (
    <div className="flex items-center justify-center gap-4 text-xs text-gray-500">
      <span className="flex items-center gap-1.5">
        <span className="inline-block h-3 w-3 rounded-sm" style={{ background: BODY_MAP_EMPTY_COLOR }} />
        기록 없음
      </span>
      <span className="flex items-center gap-1.5">
        증상 0개
        <span
          className="inline-block h-3 w-20 rounded-sm border border-gray-300"
          style={{ background: `linear-gradient(to right, ${signCountColor(0)}, ${signCountColor(SIGN_COUNT_MAX)})` }}
        />
        {SIGN_COUNT_MAX}개
      </span>
    </div>
  );
}
