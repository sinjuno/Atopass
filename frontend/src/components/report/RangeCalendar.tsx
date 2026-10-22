import { useState } from 'react';
import { monthCells, WEEKDAYS } from '@/utils/calendar';

interface DateRange {
  startDate: string;
  endDate: string;
}

interface Props {
  value: DateRange | null;
  onChange: (range: DateRange) => void;
  /** 이 날짜보다 뒤는 고를 수 없다 (yyyy-MM-dd) */
  max: string;
}

/**
 * 달력에서 기간을 고른다. 처음 누른 날이 시작일, 두 번째로 누른 날이 종료일이다
 * (앞선 날을 누르면 서로 바꾼다). 기간을 다 고른 뒤 다시 누르면 새로 고르기 시작한다.
 */
export default function RangeCalendar({ value, onChange, max }: Props) {
  const [view, setView] = useState(() => {
    const d = new Date(`${value?.endDate ?? max}T00:00:00`);
    return { year: d.getFullYear(), month: d.getMonth() };
  });
  // 시작일만 고른 상태. 종료일을 고르면 null로 돌아간다.
  const [pendingStart, setPendingStart] = useState<string | null>(null);

  const cells = monthCells(view.year, view.month);
  const maxMonth = max.slice(0, 7);
  const viewMonth = `${view.year}-${String(view.month + 1).padStart(2, '0')}`;
  const moveMonth = (delta: number) =>
    setView(({ year, month }) => {
      const next = new Date(year, month + delta, 1);
      return { year: next.getFullYear(), month: next.getMonth() };
    });

  const pick = (date: string) => {
    if (!pendingStart) {
      setPendingStart(date);
      return;
    }
    onChange(date < pendingStart ? { startDate: date, endDate: pendingStart } : { startDate: pendingStart, endDate: date });
    setPendingStart(null);
  };

  const start = pendingStart ?? value?.startDate ?? null;
  const end = pendingStart ? null : (value?.endDate ?? null);

  return (
    <div>
      <div className="mb-1 flex items-center justify-between">
        <button type="button" onClick={() => moveMonth(-1)} className="rounded-lg px-3 py-1.5 text-xl text-gray-600" aria-label="이전 달">
          ‹
        </button>
        <p className="font-bold">
          {view.year}년 {view.month + 1}월
        </p>
        <button
          type="button"
          onClick={() => moveMonth(1)}
          disabled={viewMonth >= maxMonth}
          className="rounded-lg px-3 py-1.5 text-xl text-gray-600 disabled:text-gray-200"
          aria-label="다음 달"
        >
          ›
        </button>
      </div>
      <ol className="grid grid-cols-7 text-center text-xs text-gray-500">
        {WEEKDAYS.map((day, i) => (
          <li key={day} className={`py-1 ${i === 0 ? 'text-red-400' : i === 6 ? 'text-blue-400' : ''}`}>
            {day}
          </li>
        ))}
      </ol>
      <ol className="grid grid-cols-7 gap-y-1">
        {cells.map((date, i) => {
          if (!date) return <li key={`blank-${i}`} />;
          const isEdge = date === start || date === end;
          const inRange = !!start && !!end && date > start && date < end;
          return (
            <li key={date} className={inRange ? 'bg-brand-50' : undefined}>
              <button
                type="button"
                disabled={date > max}
                onClick={() => pick(date)}
                aria-pressed={isEdge || inRange}
                aria-label={date}
                className={`mx-auto flex size-10 items-center justify-center rounded-full disabled:text-gray-200 ${
                  isEdge ? 'bg-brand-600 font-bold text-white' : inRange ? 'text-brand-800' : 'text-gray-800'
                }`}
              >
                {Number(date.slice(-2))}
              </button>
            </li>
          );
        })}
      </ol>
      <p className="mt-2 text-sm text-gray-500" aria-live="polite">
        {pendingStart ? '끝나는 날을 눌러 주세요.' : '시작하는 날과 끝나는 날을 차례로 눌러 주세요.'}
      </p>
    </div>
  );
}
