import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis, type TooltipContentProps } from 'recharts';
import type { NameType, ValueType } from 'recharts/types/component/DefaultTooltipContent';
import { EMPTY_TEXT } from '@/utils/constants';
import { formatKoreanDate } from '@/utils/date';
import { formatMonthDay } from '@/utils/dateRange';

export interface TrendDatum {
  date: string;
  value: number | null;
  recordId: number | null;
}

interface Props {
  data: TrendDatum[];
  domain: [number, number];
  /** 툴팁 값 표시 (예: 7 → "7점") */
  formatValue: (value: number) => string;
  onPointClick?: (datum: TrendDatum) => void;
  ariaLabel: string;
}

const BRAND = '#059669'; // emerald-600
const GAP = '#a8b3bf';

/**
 * 날짜별 값 변화.
 * 기록 없는 날(null)은 값을 채우지 않는다. 점선 라인(connectNulls) 위에 실선을 그려 끊긴 구간만 점선으로 보인다.
 */
export default function TrendLineChart({ data, domain, formatValue, onPointClick, ariaLabel }: Props) {
  const tooltip = ({ active, payload }: TooltipContentProps<ValueType, NameType>) => {
    if (!active || !payload?.length) return null;
    const datum = payload[0].payload as TrendDatum;
    return (
      <div className="rounded-lg bg-white px-3 py-2 text-sm shadow-md ring-1 ring-gray-200">
        <p className="font-medium text-gray-700">{formatKoreanDate(datum.date)}</p>
        <p className={datum.value === null ? 'text-gray-400' : 'text-emerald-700'}>
          {datum.value === null ? EMPTY_TEXT : formatValue(datum.value)}
        </p>
        {onPointClick && datum.recordId !== null && <p className="mt-0.5 text-gray-400">누르면 그날 기록을 볼 수 있어요</p>}
      </div>
    );
  };

  return (
    <div className={`h-56 ${onPointClick ? 'cursor-pointer' : ''}`} role="img" aria-label={ariaLabel}>
      <ResponsiveContainer width="100%" height="100%">
        <LineChart
          data={data}
          margin={{ top: 8, right: 8, bottom: 0, left: -24 }}
          onClick={(state) => {
            // 점을 정확히 누르지 않아도 가장 가까운 날짜의 기록으로 이동한다.
            const index = Number(state?.activeTooltipIndex);
            const datum = Number.isInteger(index) ? data[index] : undefined;
            if (onPointClick && datum && datum.recordId !== null && datum.value !== null) onPointClick(datum);
          }}
        >
          <CartesianGrid vertical={false} stroke="#eef1f4" />
          <XAxis
            dataKey="date"
            tickFormatter={formatMonthDay}
            tickLine={false}
            axisLine={false}
            minTickGap={24}
            tick={{ fontSize: 11, fill: '#64748b' }}
          />
          <YAxis domain={domain} tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: '#94a3b8' }} />
          <Tooltip content={tooltip} cursor={{ stroke: '#cbd5e1' }} />
          <Line
            dataKey="value"
            connectNulls
            stroke={GAP}
            strokeWidth={2}
            strokeDasharray="4 4"
            dot={false}
            activeDot={false}
            isAnimationActive={false}
          />
          <Line
            dataKey="value"
            stroke={BRAND}
            strokeWidth={2.5}
            dot={{ r: data.length > 45 ? 2.5 : 3.5, fill: BRAND, strokeWidth: 0 }}
            activeDot={{ r: 5 }}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
