import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis, type TooltipContentProps } from 'recharts';
import type { NameType, ValueType } from 'recharts/types/component/DefaultTooltipContent';
import { EMPTY_TEXT, SCORE_MAX, SCORE_MIN } from '@/utils/constants';
import { formatKoreanDate } from '@/utils/date';

export interface DailyScore {
  date: string;
  label: string;
  score: number | null;
}

interface Props {
  data: DailyScore[];
}

const BRAND = '#059669'; // emerald-600
const GAP = '#a8b3bf';

function ScoreTooltip({ active, payload }: TooltipContentProps<ValueType, NameType>) {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload as DailyScore;
  return (
    <div className="rounded-lg bg-white px-3 py-2 text-sm shadow-md ring-1 ring-gray-200">
      <p className="font-medium text-gray-700">{formatKoreanDate(point.date)}</p>
      <p className={point.score === null ? 'text-gray-400' : 'text-emerald-700'}>
        {point.score === null ? EMPTY_TEXT : `가장 높은 증상 점수 ${point.score}점`}
      </p>
    </div>
  );
}

/**
 * 최근 7일 증상 점수.
 * 기록 없는 날은 값을 채우지 않는다. 점선 라인(connectNulls)을 깔고 그 위에 실선을 그려
 * 기록이 끊긴 구간만 점선으로 보이게 한다.
 */
export default function WeeklyScoreChart({ data }: Props) {
  return (
    <div>
      <div className="h-44" role="img" aria-label="최근 7일 증상 점수 그래프">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: -24 }}>
            <CartesianGrid vertical={false} stroke="#eef1f4" />
            <XAxis dataKey="label" tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: '#64748b' }} />
            <YAxis
              domain={[SCORE_MIN, SCORE_MAX]}
              ticks={[0, 5, 10]}
              tickLine={false}
              axisLine={false}
              tick={{ fontSize: 11, fill: '#94a3b8' }}
            />
            <Tooltip content={ScoreTooltip} cursor={{ stroke: '#cbd5e1' }} />
            <Line
              dataKey="score"
              connectNulls
              stroke={GAP}
              strokeWidth={2}
              strokeDasharray="4 4"
              dot={false}
              activeDot={false}
              isAnimationActive={false}
            />
            <Line
              dataKey="score"
              stroke={BRAND}
              strokeWidth={2.5}
              dot={{ r: 3.5, fill: BRAND, strokeWidth: 0 }}
              activeDot={{ r: 5 }}
              isAnimationActive={false}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>
      <p className="mt-2 flex items-center gap-1.5 text-sm text-gray-400">
        <svg width="18" height="4" aria-hidden>
          <line x1="0" y1="2" x2="18" y2="2" stroke={GAP} strokeWidth="2" strokeDasharray="4 3" />
        </svg>
        기록이 없는 날
      </p>
    </div>
  );
}
