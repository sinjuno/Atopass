import type { BodyPart, SkinCondition, SymptomKey } from '@/types/record';
import { BODY_PART_LABELS, EMPTY_TEXT, SCORE_MAX, SCORE_MIN, SYMPTOM_LABELS } from '@/utils/constants';

interface Props {
  value: SkinCondition[];
  onChange: (value: SkinCondition[]) => void;
}

const BODY_PARTS = Object.keys(BODY_PART_LABELS) as BodyPart[];
const SYMPTOMS = Object.keys(SYMPTOM_LABELS) as SymptomKey[];
const SCORES = Array.from({ length: SCORE_MAX - SCORE_MIN + 1 }, (_, i) => SCORE_MIN + i);

/** 부위별 증상 점수 (0~10, 미선택은 "기록 없음" — 0으로 채우지 않는다) */
export default function SkinConditionEditor({ value, onChange }: Props) {
  const unusedParts = BODY_PARTS.filter((p) => !value.some((c) => c.bodyPart === p));

  const update = (index: number, next: SkinCondition) => onChange(value.map((c, i) => (i === index ? next : c)));

  const setScore = (index: number, key: SymptomKey, raw: string) => {
    const { [key]: _old, ...rest } = value[index].symptoms;
    update(index, { ...value[index], symptoms: raw === '' ? rest : { ...rest, [key]: Number(raw) } });
  };

  return (
    <div className="flex flex-col gap-4">
      {value.length === 0 && <p className="text-gray-400">{EMPTY_TEXT}</p>}

      {value.map((condition, index) => (
        <div key={condition.bodyPart} className="rounded-xl border border-gray-200 p-4">
          <div className="mb-3 flex items-center justify-between">
            <select
              value={condition.bodyPart}
              onChange={(e) => update(index, { ...condition, bodyPart: e.target.value as BodyPart })}
              className="rounded-lg border border-gray-300 px-3 py-2 text-base font-semibold"
              aria-label="부위"
            >
              {[condition.bodyPart, ...unusedParts].map((p) => (
                <option key={p} value={p}>
                  {BODY_PART_LABELS[p]}
                </option>
              ))}
            </select>
            <button
              type="button"
              onClick={() => onChange(value.filter((_, i) => i !== index))}
              className="text-sm text-red-500"
            >
              부위 삭제
            </button>
          </div>

          <dl className="grid grid-cols-2 gap-x-4 gap-y-2">
            {SYMPTOMS.map((key) => (
              <div key={key} className="flex items-center justify-between gap-2">
                <dt className="text-base text-gray-700">{SYMPTOM_LABELS[key]}</dt>
                <dd>
                  <select
                    value={condition.symptoms[key] ?? ''}
                    onChange={(e) => setScore(index, key, e.target.value)}
                    className="rounded-lg border border-gray-300 px-2 py-1 text-base"
                    aria-label={`${BODY_PART_LABELS[condition.bodyPart]} ${SYMPTOM_LABELS[key]} 점수`}
                  >
                    <option value="">{EMPTY_TEXT}</option>
                    {SCORES.map((s) => (
                      <option key={s} value={s}>
                        {s}점
                      </option>
                    ))}
                  </select>
                </dd>
              </div>
            ))}
          </dl>
        </div>
      ))}

      {unusedParts.length > 0 && (
        <button
          type="button"
          onClick={() => onChange([...value, { bodyPart: unusedParts[0], symptoms: {} }])}
          className="rounded-xl border border-dashed border-gray-300 py-3 text-base text-gray-600"
        >
          + 부위 추가
        </button>
      )}
    </div>
  );
}
