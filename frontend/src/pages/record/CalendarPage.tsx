import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { getAllRecords } from '@/apis/recordList';
import PageHeader from '@/components/common/PageHeader';
import { ErrorMessage } from '@/components/common/StatusMessage';
import { useAsync } from '@/hooks/useAsync';
import { toDateString, today } from '@/utils/date';
import { getErrorMessage } from '@/utils/errorMessage';
import { representativeScore } from '@/utils/recordScore';
import { recordPath } from '@/utils/routes';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

/** 대표 점수(가장 높은 증상 점수) 구간별 표시 색 */
const SCORE_LEVELS = [
  { max: 3, label: '0~3점', className: 'bg-emerald-500' },
  { max: 6, label: '4~6점', className: 'bg-amber-400' },
  { max: 10, label: '7~10점', className: 'bg-red-500' },
];
const scoreColor = (score: number) => SCORE_LEVELS.find((level) => score <= level.max)!.className;

/** 해당 달의 1일 ~ 말일 (yyyy-MM-dd) */
function monthRange(year: number, month: number) {
  return { startDate: toDateString(new Date(year, month, 1)), endDate: toDateString(new Date(year, month + 1, 0)) };
}

/** 달력 칸: 앞쪽 빈칸(null) + 1일부터 말일까지 */
function monthCells(year: number, month: number): (string | null)[] {
  const blanks = new Date(year, month, 1).getDay();
  const days = new Date(year, month + 1, 0).getDate();
  return [
    ...Array<null>(blanks).fill(null),
    ...Array.from({ length: days }, (_, i) => toDateString(new Date(year, month, i + 1))),
  ];
}

/** 캘린더 — /calendar. 월별 기록 여부와 대표 점수를 보여 주고, 날짜를 누르면 그날 기록으로 이동한다 */
export default function CalendarPage() {
  const navigate = useNavigate();
  const todayString = today();
  const [view, setView] = useState(() => {
    const now = new Date();
    return { year: now.getFullYear(), month: now.getMonth() };
  });

  const range = useMemo(() => monthRange(view.year, view.month), [view]);
  const cells = useMemo(() => monthCells(view.year, view.month), [view]);
  const records = useAsync(() => getAllRecords(range.startDate, range.endDate), [range]);

  const recordByDate = useMemo(
    () => new Map((records.data ?? []).map((record) => [record.recordDate, record])),
    [records.data],
  );

  const isCurrentMonth = range.endDate >= todayString;
  const moveMonth = (delta: number) =>
    setView(({ year, month }) => {
      const next = new Date(year, month + delta, 1);
      return { year: next.getFullYear(), month: next.getMonth() };
    });

  return (
    <>
      <PageHeader title="캘린더" />

      <div className="flex flex-col gap-4 px-4 pt-4">
        <div className="flex items-center justify-between">
          <button
            type="button"
            onClick={() => moveMonth(-1)}
            className="rounded-lg px-3 py-2 text-xl text-gray-600"
            aria-label="이전 달"
          >
            ‹
          </button>
          <h2 className="text-xl font-bold">
            {view.year}년 {view.month + 1}월
          </h2>
          <button
            type="button"
            onClick={() => moveMonth(1)}
            disabled={isCurrentMonth}
            className="rounded-lg px-3 py-2 text-xl text-gray-600 disabled:text-gray-200"
            aria-label="다음 달"
          >
            ›
          </button>
        </div>

        {records.error ? (
          <ErrorMessage message={getErrorMessage(records.error)} onRetry={records.reload} />
        ) : (
          <div className={records.loading ? 'opacity-50' : undefined} aria-busy={records.loading}>
            <ol className="grid grid-cols-7 text-center text-sm text-gray-500">
              {WEEKDAYS.map((day, i) => (
                <li key={day} className={`py-1 ${i === 0 ? 'text-red-400' : i === 6 ? 'text-blue-400' : ''}`}>
                  {day}
                </li>
              ))}
            </ol>
            <ol className="grid grid-cols-7 gap-1">
              {cells.map((date, i) => {
                if (!date) return <li key={`blank-${i}`} />;
                const record = recordByDate.get(date);
                const score = representativeScore(record?.skinConditions);
                const isFuture = date > todayString;
                const isToday = date === todayString;
                const day = Number(date.slice(-2));
                return (
                  <li key={date}>
                    <button
                      type="button"
                      disabled={isFuture}
                      onClick={() => navigate(recordPath(date))}
                      aria-label={`${view.month + 1}월 ${day}일${record ? `, 기록 있음${score === null ? '' : `, 최고 ${score}점`}` : ''}`}
                      className={`flex aspect-square w-full flex-col items-center justify-center gap-1 rounded-xl text-base ${
                        isToday ? 'border-2 border-emerald-600 font-bold' : ''
                      } ${isFuture ? 'text-gray-300' : 'hover:bg-gray-50'}`}
                    >
                      {day}
                      <span
                        className={`size-2 rounded-full ${
                          record ? (score === null ? 'bg-gray-400' : scoreColor(score)) : 'bg-transparent'
                        }`}
                      />
                    </button>
                  </li>
                );
              })}
            </ol>
          </div>
        )}

        <section className="rounded-2xl border border-gray-200 p-4 text-sm text-gray-600">
          <p className="mb-2">
            이번 달 기록 <span className="font-bold text-gray-900">{records.data?.length ?? 0}일</span>
          </p>
          <ul className="flex flex-wrap gap-x-4 gap-y-1">
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
          <p className="mt-2 text-gray-400">점은 그날 기록한 증상 중 가장 높은 점수예요. 날짜를 누르면 그날 기록을 볼 수 있어요.</p>
        </section>
      </div>
    </>
  );
}
