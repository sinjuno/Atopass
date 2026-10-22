import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { analysisApi } from '@/apis/analysis';
import { getAllRecords } from '@/apis/recordList';
import PageHeader from '@/components/common/PageHeader';
import { ErrorMessage, LoadingMessage } from '@/components/common/StatusMessage';
import ComparisonView from '@/components/record/ComparisonView';
import { useAsync } from '@/hooks/useAsync';
import { monthCells, monthRange, SCORE_LEVELS, scoreColor, WEEKDAYS } from '@/utils/calendar';
import { today } from '@/utils/date';
import { getErrorMessage } from '@/utils/errorMessage';
import { recordPath } from '@/utils/routes';

/**
 * 캘린더 — /calendar. 월별 기록 여부와 대표 점수를 보여 주고, 날짜를 누르면 그날 기록으로 이동한다.
 * 비교 모드에서는 날짜 두 개(A·B)를 골라 그날 기록을 나란히 비교한다. A가 앞선 날짜다.
 */
export default function CalendarPage() {
  const navigate = useNavigate();
  const todayString = today();
  const [view, setView] = useState(() => {
    const now = new Date();
    return { year: now.getFullYear(), month: now.getMonth() };
  });

  const [comparing, setComparing] = useState(false);
  const [picked, setPicked] = useState<string[]>([]);
  const [dateA, dateB] = picked;
  const comparison = useAsync(
    () => (dateA && dateB ? analysisApi.getComparison({ dateA, dateB }) : Promise.resolve(null)),
    [dateA, dateB],
  );

  /** 고른 날짜를 다시 누르면 빼고, 이미 두 개면 나중 날짜(B)를 바꾼다 */
  const pick = (date: string) =>
    setPicked((prev) => {
      if (prev.includes(date)) return prev.filter((d) => d !== date);
      return [...prev.slice(0, 1), date].sort();
    });

  const toggleComparing = () => {
    setComparing((on) => !on);
    setPicked([]);
  };

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

      <div className="flex flex-col gap-4 px-5 pt-4 pb-4">
        <div className="flex items-center justify-between gap-3 rounded-2xl bg-white px-4 py-3 shadow-card">
          <p className="text-sm text-gray-600">
            {comparing
              ? picked.length < 2
                ? `비교할 날짜를 ${2 - picked.length}개 더 골라 주세요`
                : '고른 날짜를 다시 누르면 선택이 풀려요'
              : '두 날짜의 기록을 나란히 볼 수 있어요'}
          </p>
          <button
            type="button"
            onClick={toggleComparing}
            aria-pressed={comparing}
            className={`shrink-0 rounded-lg px-3 py-2 text-sm font-semibold ${
              comparing ? 'bg-brand-600 text-white' : 'border border-gray-200 bg-white text-gray-700'
            }`}
          >
            {comparing ? '비교 끝내기' : '날짜 비교'}
          </button>
        </div>

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
          <div
            className={`rounded-3xl bg-white p-3 shadow-card ${records.loading ? 'opacity-50' : ''}`}
            aria-busy={records.loading}
          >
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
                const score = record?.painIndex ?? null;
                const isFuture = date > todayString;
                const isToday = date === todayString;
                const day = Number(date.slice(-2));
                const tag = date === dateA ? 'A' : date === dateB ? 'B' : null;
                return (
                  <li key={date}>
                    <button
                      type="button"
                      disabled={isFuture}
                      onClick={() => (comparing ? pick(date) : navigate(recordPath(date)))}
                      aria-pressed={comparing ? tag !== null : undefined}
                      aria-label={`${view.month + 1}월 ${day}일${record ? `, 기록 있음${score === null ? '' : `, 최고 ${score}점`}` : ''}${tag ? `, 비교 ${tag}` : ''}`}
                      className={`relative flex aspect-square w-full flex-col items-center justify-center gap-1 rounded-xl text-base ${
                        isToday ? 'border-2 border-brand-600 font-bold' : ''
                      } ${tag ? 'bg-brand-600 text-white' : isFuture ? 'text-gray-300' : 'hover:bg-brand-50'}`}
                    >
                      {tag && <span className="absolute left-1 top-0.5 text-[10px] font-bold">{tag}</span>}
                      {day}
                      <span
                        className={`size-2 rounded-full ${
                          record ? scoreColor(score) : 'bg-transparent'
                        }`}
                      />
                    </button>
                  </li>
                );
              })}
            </ol>
          </div>
        )}

        {comparing && dateA && dateB && (
          <section aria-label="날짜 비교 결과" aria-busy={comparison.loading}>
            {comparison.error ? (
              <ErrorMessage message={getErrorMessage(comparison.error)} onRetry={comparison.reload} />
            ) : comparison.loading || !comparison.data ? (
              <LoadingMessage />
            ) : (
              <ComparisonView dateA={dateA} dateB={dateB} comparison={comparison.data} />
            )}
          </section>
        )}

        <section className="rounded-2xl bg-white p-4 text-sm text-gray-600 shadow-card">
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
          <p className="mt-2 text-gray-400">점은 그날 기록한 고통 지수예요. {comparing ? '비교 모드에서는 기록이 없는 날도 고를 수 있어요.' : '날짜를 누르면 그날 기록을 볼 수 있어요.'}</p>
        </section>
      </div>
    </>
  );
}
