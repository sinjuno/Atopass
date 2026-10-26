import { useState } from 'react';
import type { Insight } from '@/types/analysis';
import { BODY_PART_LABELS, SYMPTOM_LABELS } from '@/utils/constants';

/** 인사이트가 무엇에 대한 것인지. 불편함 지수 변화는 bodyPart·symptom이 null이다 */
function insightSubject(insight: Insight) {
  if (insight.type === 'PAIN_INDEX_CHANGE') return '불편함 지수';
  const part = insight.bodyPart ? BODY_PART_LABELS[insight.bodyPart] : null;
  const symptom = insight.symptom ? SYMPTOM_LABELS[insight.symptom] : null;
  return [part, symptom].filter(Boolean).join(' ') || null;
}

/**
 * 선택한 기간에 눈에 띈 변화(분석 요약). 그래프를 위로 올리기 위해 그래프 아래에 접어 둔다.
 * 처음에는 제목만 보이고, 누르면 문구를 펼친다.
 */
export default function PeriodInsights({ insights }: { insights: Insight[] }) {
  const [open, setOpen] = useState(false);
  if (insights.length === 0) return null;

  return (
    <section className="rounded-3xl bg-white shadow-card">
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        className="flex w-full items-center justify-between px-4 py-3 text-left"
      >
        <span className="font-bold text-gray-700">
          이 기간에 눈에 띈 변화 <span className="text-brand-700">{insights.length}</span>
        </span>
        <span aria-hidden className={`text-gray-400 transition-transform ${open ? 'rotate-180' : ''}`}>
          ▾
        </span>
      </button>
      {open && (
        <ul className="flex flex-col divide-y divide-gray-100 border-t border-gray-100 px-4">
          {insights.map((insight, i) => {
            const subject = insightSubject(insight);
            return (
              <li key={`${insight.type}-${insight.bodyPart ?? ''}-${insight.symptom ?? ''}-${i}`} className="py-3">
                {subject && (
                  <span className="mb-1 inline-block rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600">
                    {insight.direction === 'LOW' ? '▼ ' : insight.direction === 'HIGH' ? '▲ ' : ''}
                    {subject}
                  </span>
                )}
                <p className="text-gray-800">{insight.message}</p>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
