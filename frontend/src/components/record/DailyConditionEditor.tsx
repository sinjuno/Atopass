import type { SkinCondition, SkinSign } from '@/types/record';
import { allSkinSigns } from '@/utils/bodyMap';
import { SKIN_SIGN_LABELS } from '@/utils/constants';

interface Value {
  medication: boolean | null;
  skinSigns: SkinSign[];
}

interface Props {
  medication: boolean | null | undefined;
  /** 부위 없이 고른 증상(예전 기록·챗봇). 부위마다 고른 증상은 skinConditions에 있다 */
  skinSigns: SkinSign[] | null | undefined;
  skinConditions: SkinCondition[];
  onChange: (next: Value) => void;
}

/**
 * 약 복용 여부(예/아니오) + 나타난 증상. 불편함 지수는 부위와 한 묶음으로 따로 받는다.
 * 나타난 증상은 부위별 기록에서 고른 증상을 모두 합쳐 보여 준다(예: 목 홍반, 왼손 진물/딱지 → 홍반, 각질).
 * 부위 없이 남은 증상(예전 기록·챗봇)은 여기서 지울 수 있다.
 */
export default function DailyConditionEditor({ medication, skinSigns, skinConditions, onChange }: Props) {
  const current: Value = { medication: medication ?? null, skinSigns: skinSigns ?? [] };
  const shownSigns = allSkinSigns({ skinConditions, skinSigns: current.skinSigns });

  const removeDailySign = (sign: SkinSign) =>
    onChange({ ...current, skinSigns: current.skinSigns.filter((s) => s !== sign) });

  return (
    <div className="flex flex-col gap-5">
      <fieldset className="flex items-center justify-between gap-3">
        <legend className="sr-only">약 복용 여부</legend>
        <span className="text-base text-gray-700">약 복용</span>
        <div className="flex gap-1">
          {[
            { value: true, label: '예' },
            { value: false, label: '아니오' },
          ].map((o) => {
            const selected = current.medication === o.value;
            return (
              <button
                key={o.label}
                type="button"
                aria-pressed={selected}
                // 선택한 버튼을 다시 누르면 선택 해제(null = 기록 없음)
                onClick={() => onChange({ ...current, medication: selected ? null : o.value })}
                className={`rounded-full border px-4 py-1.5 text-base ${
                  selected ? 'border-brand-600 bg-brand-50 font-semibold text-brand-700' : 'border-gray-300 text-gray-600'
                }`}
              >
                {o.label}
              </button>
            );
          })}
        </div>
      </fieldset>

      <div>
        <p className="mb-2 text-base text-gray-700">나타난 증상</p>
        {shownSigns.length === 0 ? (
          <p className="text-sm text-gray-400">위 마네킹에서 부위를 누르고 증상을 골라 주세요.</p>
        ) : (
          <ul className="flex flex-wrap gap-2">
            {shownSigns.map((sign) => {
              // 부위에서 고른 증상은 부위에서 지우고, 부위 없이 남은 증상만 여기서 지운다
              const removable = current.skinSigns.includes(sign) && !partHasSign(skinConditions, sign);
              return (
                <li
                  key={sign}
                  className="flex items-center gap-1 rounded-full border border-brand-600 bg-brand-50 px-3 py-1 text-base text-brand-700"
                >
                  {SKIN_SIGN_LABELS[sign]}
                  {removable && (
                    <button
                      type="button"
                      onClick={() => removeDailySign(sign)}
                      aria-label={`${SKIN_SIGN_LABELS[sign]} 지우기`}
                      className="-mr-1 px-1 text-brand-700"
                    >
                      ×
                    </button>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}

const partHasSign = (skinConditions: SkinCondition[], sign: SkinSign) =>
  skinConditions.some((c) => c.skinSigns?.includes(sign));
