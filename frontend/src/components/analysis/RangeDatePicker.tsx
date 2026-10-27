import { useMemo, useState } from 'react';
import { getAllRecords } from '@/apis/recordList';
import { useAsync } from '@/hooks/useAsync';
import { monthCells, monthRange, SCORE_LEVELS, scoreColor, WEEKDAYS } from '@/utils/calendar';
import { today } from '@/utils/date';
import { formatFullDate } from '@/utils/dateRange';

export type DateRange = { startDate: string; endDate: string };

/**
 * 그래프 '직접 선택' 기간을 고르는 달력. 캘린더 탭처럼 기록한 날에 불편함 지수 색 점을 찍어,
 * 어느 기간을 볼지 기록을 보면서 고를 수 있게 한다. 첫 번째로 누른 날이 시작, 두 번째로 누른 날이 끝이다(순서는 맞춰 준다).
 */
export default function RangeDatePicker({ value, onChange }: { value: DateRange; onChange: (range: DateRange) => void }) {
  const todayString = today();
  const [open, setOpen] = useState(false);
  // 시작 날짜만 고르고 끝 날짜를 기다리는 중이면 그 날짜
  const [pending, setPending] = useState<string | null>(null);
  const [view, setView] = useState(() => {
    const [y, m] = value.endDate.split('-').map(Number);
    return { year: y, month: m - 1 };
  });

  const range = useMemo(() => monthRange(view.year, view.month), [view]);
  const cells = useMemo(() => monthCells(view.year, view.month), [view]);
  const records = useAsync(() => (open ? getAllRecords(range.startDate, range.endDate) : Promise.resolve([])), [open, range]);
  const scoreByDate = useMemo(
    () => new Map((records.data ?? []).map((record) => [record.recordDate, record.painIndex ?? null])),
    [records.data],
  );

  const isCurrentMonth = range.endDate >= todayString;
  const moveMonth = (delta: number) =>
    setView(({ year, month }) => {
      const next = new Date(year, month + delta, 1);
      return { year: next.getFullYear(), month: next.getMonth() };
    });

  const toggleOpen = () => {
    setOpen((on) => !on);
    setPending(null);
  };

  const pick = (date: string) => {
    if (!pending) {
      setPending(date);
      return;
    }
    onChange(pending <= date ? { startDate: pending, endDate: date } : { startDate: date, endDate: pending });
    setPending(null);
    setOpen(false);
  };

  // 끝 날짜를 기다리는 동안에는 시작 날짜 하나만, 아니면 지금 기간을 표시한다.
  const shownStart = pending ?? value.startDate;
  const shownEnd = pending ?? value.endDate;

  return (
    <div className="-mt-1 flex flex-col gap-2">
      <button
        type="button"
        onClick={toggleOpen}
        aria-expanded={open}
        className="flex items-center justify-between rounded-xl border border-gray-200 bg-white px-4 py-2.5 text-left font-medium text-gray-800"
      >
        <span>
          {formatFullDate(value.startDate)} ~ {formatFullDate(value.endDate)}
        </span>
        <span aria-hidden="true" className="text-sm text-gray-400">
          {open ? '닫기 ▴' : '바꾸기 ▾'}
        </span>
      </button>

      {open && (
        <div className="rounded-2xl bg-white p-3 shadow-card" aria-busy={records.loading}>
          <p className="mb-2 text-center text-sm text-brand-700">
            {pending ? `${formatFullDate(pending)}부터, 끝 날짜를 골라 주세요` : '시작 날짜를 골라 주세요'}
          </p>
          <div className="flex items-center justify-between">
            <button type="button" onClick={() => moveMonth(-1)} className="rounded-lg px-3 py-1 text-xl text-gray-600" aria-label="이전 달">
              ‹
            </button>
            <p className="font-bold">
              {view.year}년 {view.month + 1}월
            </p>
            <button
              type="button"
              onClick={() => moveMonth(1)}
              disabled={isCurrentMonth}
              className="rounded-lg px-3 py-1 text-xl text-gray-600 disabled:text-gray-200"
              aria-label="다음 달"
            >
              ›
            </button>
          </div>
          <ol className="grid grid-cols-7 text-center text-sm text-gray-500">
            {WEEKDAYS.map((day, i) => (
              <li key={day} className={`py-1 ${i === 0 ? 'text-red-400' : i === 6 ? 'text-blue-400' : ''}`}>
                {day}
              </li>
            ))}
          </ol>
          <ol className={`grid grid-cols-7 gap-y-1 ${records.loading ? 'opacity-50' : ''}`}>
            {cells.map((date, i) => {
              if (!date) return <li key={`blank-${i}`} />;
              const recorded = scoreByDate.has(date);
              const isFuture = date > todayString;
              const isEdge = date === shownStart || date === shownEnd;
              const inRange = date > shownStart && date < shownEnd;
              const day = Number(date.slice(-2));
              return (
                <li key={date} className={inRange ? 'bg-brand-50' : ''}>
                  <button
                    type="button"
                    disabled={isFuture}
                    onClick={() => pick(date)}
                    aria-pressed={isEdge}
                    aria-label={`${view.month + 1}월 ${day}일${recorded ? `, 기록 있음${scoreByDate.get(date) === null ? '' : `, ${scoreByDate.get(date)}점`}` : ''}`}
                    className={`flex h-11 w-full flex-col items-center justify-center gap-0.5 rounded-xl text-base ${
                      isEdge ? 'bg-brand-600 font-bold text-white' : isFuture ? 'text-gray-300' : 'hover:bg-brand-100'
                    } ${date === todayString && !isEdge ? 'font-bold text-brand-700' : ''}`}
                  >
                    {day}
                    <span
                      className={`size-1.5 rounded-full ${recorded ? scoreColor(scoreByDate.get(date) ?? null) : 'bg-transparent'} ${
                        isEdge && recorded ? 'ring-1 ring-white' : ''
                      }`}
                    />
                  </button>
                </li>
              );
            })}
          </ol>
          <ul className="mt-2 flex flex-wrap justify-center gap-x-3 gap-y-1 text-xs text-gray-500">
            {SCORE_LEVELS.map((level) => (
              <li key={level.label} className="flex items-center gap-1">
                <span className={`size-2 rounded-full ${level.className}`} />
                {level.label}
              </li>
            ))}
            <li className="flex items-center gap-1">
              <span className="size-2 rounded-full bg-gray-400" />
              점수 없음
            </li>
          </ul>
        </div>
      )}
    </div>
  );
}
