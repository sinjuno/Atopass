import { useState } from 'react';
import type { BodyPart, SkinCondition, SkinSign } from '@/types/record';
import { bodyMapSignCounts, isBodyMapPart, sortSkinSigns } from '@/utils/bodyMap';
import { BODY_PART_LABELS, PART_PAIN_SYMPTOM, SKIN_SIGN_LABELS } from '@/utils/constants';
import BodyMap, { BodyMapLegend } from './BodyMap';

interface Props {
  value: SkinCondition[];
  onChange: (value: SkinCondition[]) => void;
  /** 그날의 고통 지수 — 고통 지수와 부위는 한 묶음이라, 고른 부위의 고통 점수로 그대로 들어간다 */
  painIndex: number | null;
}

const SKIN_SIGNS = Object.keys(SKIN_SIGN_LABELS) as SkinSign[];

/**
 * 고통이 있는 부위와 그 부위에 나타난 증상. 마네킹에서 부위를 누르면 기록하고, 그 부위에 나타난 증상을 고른다.
 * 부위마다 점수를 따로 받지 않고 그날의 고통 지수를 부위의 고통 점수(symptoms.pain)로 저장한다.
 * 부위 색은 나타난 증상 개수에 비례한다(0개 하얀색 → 7개 새빨간색, 기록 없음은 회색).
 */
export default function SkinConditionEditor({ value, onChange, painIndex }: Props) {
  const [selected, setSelected] = useState<BodyPart | null>(null);
  const counts = bodyMapSignCounts(value);
  const legacy = value.filter((c) => !isBodyMapPart(c.bodyPart));
  const selectedCondition = value.find((c) => c.bodyPart === selected);
  const selectedSigns = selectedCondition?.skinSigns ?? [];

  // 기록하지 않은 부위를 누르면 고통 지수로 기록하고 고른다. 고통 지수를 아직 안 골랐으면 고르기만 한다.
  const select = (part: BodyPart) => {
    setSelected(part);
    if (painIndex === null || value.some((c) => c.bodyPart === part)) return;
    onChange([...value, { bodyPart: part, symptoms: { [PART_PAIN_SYMPTOM]: painIndex }, skinSigns: [] }]);
  };

  const toggleSign = (part: BodyPart, sign: SkinSign) =>
    onChange(
      value.map((c) => {
        if (c.bodyPart !== part) return c;
        const signs = c.skinSigns ?? [];
        return { ...c, skinSigns: signs.includes(sign) ? signs.filter((s) => s !== sign) : sortSkinSigns([...signs, sign]) };
      }),
    );

  const removePart = (part: BodyPart) => onChange(value.filter((c) => c.bodyPart !== part));

  return (
    <div className="flex flex-col gap-4">
      <div className="rounded-xl border border-gray-200 p-4">
        <BodyMap counts={counts} selected={selected} onSelect={select} />
        <div className="mt-3">
          <BodyMapLegend />
        </div>
      </div>

      {selected && selectedCondition ? (
        <div className="rounded-xl bg-gray-50 p-4">
          <div className="mb-3 flex items-center justify-between">
            <p className="text-base font-semibold text-gray-800">{BODY_PART_LABELS[selected]}에 나타난 증상</p>
            <button type="button" onClick={() => removePart(selected)} className="text-sm text-red-500">
              부위 지우기
            </button>
          </div>
          <div className="flex flex-wrap gap-2" role="group" aria-label={`${BODY_PART_LABELS[selected]}에 나타난 증상`}>
            {SKIN_SIGNS.map((sign) => {
              const checked = selectedSigns.includes(sign);
              return (
                <button
                  key={sign}
                  type="button"
                  aria-pressed={checked}
                  onClick={() => toggleSign(selected, sign)}
                  className={`rounded-full border px-3 py-1.5 text-base ${
                    checked ? 'border-emerald-600 bg-emerald-50 font-semibold text-emerald-700' : 'border-gray-300 bg-white text-gray-700'
                  }`}
                >
                  {SKIN_SIGN_LABELS[sign]}
                </button>
              );
            })}
          </div>
          <p className="mt-2 text-sm text-gray-400">증상이 많을수록 부위가 붉게 칠해져요.</p>
        </div>
      ) : (
        <p className="text-center text-sm text-gray-500">
          {painIndex === null
            ? '고통 지수를 먼저 고른 뒤 마네킹에서 고통이 있는 부위를 눌러 주세요.'
            : '마네킹에서 고통이 있는 부위를 누르고 나타난 증상을 골라 주세요.'}
        </p>
      )}

      {legacy.length > 0 && (
        <div className="rounded-xl border border-dashed border-gray-300 p-4">
          <p className="mb-2 text-sm text-gray-500">예전 방식으로 기록한 부위예요. 지우고 마네킹에서 다시 골라 주세요.</p>
          <ul className="flex flex-col gap-2">
            {legacy.map((c) => (
              <li key={c.bodyPart} className="flex items-center justify-between">
                <span className="text-base text-gray-700">
                  {BODY_PART_LABELS[c.bodyPart]} 증상 {c.skinSigns?.length ?? 0}개
                </span>
                <button type="button" onClick={() => removePart(c.bodyPart)} className="text-sm text-red-500">
                  지우기
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
