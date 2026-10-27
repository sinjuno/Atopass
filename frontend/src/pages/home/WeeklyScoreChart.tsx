import {
  Area,
  CartesianGrid,
  ComposedChart,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  type TooltipContentProps,
} from 'recharts';
import type { NameType, ValueType } from 'recharts/types/component/DefaultTooltipContent';
import { CHART, SCORE_TICKS } from '@/components/analysis/chartTheme';
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

function ScoreTooltip({ active, payload }: TooltipContentProps<ValueType, NameType>) {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload as DailyScore;
  return (
    <div className="rounded-lg bg-white px-3 py-2 text-sm shadow-md ring-1 ring-gray-200">
      <p className="font-medium text-gray-700">{formatKoreanDate(point.date)}</p>
      <p className={point.score === null ? 'text-gray-400' : 'font-semibold text-gray-900'}>
        {point.score === null ? EMPTY_TEXT : `불편함 지수 ${point.score}점`}
      </p>
    </div>
  );
}

/**
 * 최근 7일 불편함 지수.
 * 기록 없는 날은 값을 채우지 않는다. 점선 라인(connectNulls)을 깔고 그 위에 실선을 그려
 * 기록이 끊긴 구간만 점선으로 보이게 한다. 7일뿐이라 점마다 점수를 붙인다.
 */
export default function WeeklyScoreChart({ data }: Props) {
  return (
    <div>
      <div className="h-52" role="img" aria-label="최근 7일 불편함 지수 그래프">
        <ResponsiveContainer width="100%" height="100%">
          <ComposedChart data={data} margin={{ top: 20, right: 12, bottom: 0, left: -20 }}>
            <CartesianGrid vertical={false} stroke={CHART.grid} />
            <XAxis dataKey="label" tickLine={false} axisLine={false} tick={CHART.tick} />
            <YAxis
              domain={[SCORE_MIN, SCORE_MAX]}
              ticks={SCORE_TICKS}
              interval={0}
              tickLine={false}
              axisLine={false}
              tick={CHART.tick}
            />
            <Tooltip content={ScoreTooltip} cursor={{ stroke: CHART.cursor }} />
            <Area
              dataKey="score"
              stroke="none"
              fill={CHART.line}
              fillOpacity={CHART.areaOpacity}
              activeDot={false}
              isAnimationActive={false}
            />
            <Line
              dataKey="score"
              connectNulls
              stroke={CHART.gap}
              strokeWidth={2}
              strokeDasharray="4 4"
              dot={false}
              activeDot={false}
              isAnimationActive={false}
            />
            <Line
              dataKey="score"
              stroke={CHART.line}
              strokeWidth={2.5}
              strokeLinejoin="round"
              strokeLinecap="round"
              dot={{ r: 4, fill: CHART.line, stroke: CHART.surface, strokeWidth: 2 }}
              activeDot={{ r: 6, fill: CHART.line, stroke: CHART.surface, strokeWidth: 2 }}
              label={{ position: 'top', fontSize: 12, fontWeight: 600, fill: CHART.label, offset: 8 }}
              isAnimationActive={false}
            />
          </ComposedChart>
        </ResponsiveContainer>
      </div>
      <p className="mt-2 flex items-center gap-1.5 text-sm text-gray-500">
        <svg width="18" height="4" aria-hidden>
          <line x1="0" y1="2" x2="18" y2="2" stroke={CHART.gap} strokeWidth="2" strokeDasharray="4 3" />
        </svg>
        기록이 없는 날
      </p>
    </div>
  );
}
