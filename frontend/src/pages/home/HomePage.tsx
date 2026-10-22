import { useMemo } from 'react';
import { Link } from 'react-router';
import { memberApi } from '@/apis/member';
import { getAllRecords } from '@/apis/recordList';
import AccountMenu from '@/components/common/AccountMenu';
import Disclaimer from '@/components/common/Disclaimer';
import PageHeader from '@/components/common/PageHeader';
import { ErrorMessage, LoadingMessage } from '@/components/common/StatusMessage';
import { useAsync } from '@/hooks/useAsync';
import { formatKoreanDate, today } from '@/utils/date';
import { eachDay, formatMonthDay, lastNDaysRange } from '@/utils/dateRange';
import { getErrorMessage } from '@/utils/errorMessage';
import { recordPath } from '@/utils/routes';
import VisitCard from './VisitCard';
import WeatherCard from './WeatherCard';
import WeeklyScoreChart, { type DailyScore } from './WeeklyScoreChart';

const CHART_DAYS = 7;
/** 홈에 보여줄 최근 기록 목록 개수 */
const RECENT_RECORDS_COUNT = 3;

export default function HomePage() {
  const todayString = today();
  const range = useMemo(() => lastNDaysRange(CHART_DAYS), []);

  const me = useAsync(memberApi.getMe, []);
  // 오늘을 포함한 7일 기록: 오늘 기록 여부와 차트에 함께 쓴다.
  const records = useAsync(() => getAllRecords(range.startDate, range.endDate), [range]);

  const todayRecord = records.data?.find((record) => record.recordDate === todayString);
  const todayScore = todayRecord?.painIndex ?? null;

  const chartData = useMemo<DailyScore[]>(() => {
    const byDate = new Map((records.data ?? []).map((record) => [record.recordDate, record]));
    return eachDay(range.startDate, range.endDate).map((date) => ({
      date,
      label: date === todayString ? '오늘' : formatMonthDay(date),
      score: byDate.get(date)?.painIndex ?? null,
    }));
  }, [records.data, range, todayString]);

  const hasAnyScore = chartData.some((day) => day.score !== null);
  const recordedDaysCount = (records.data ?? []).length;
  const recentRecords = useMemo(
    () => [...(records.data ?? [])].sort((a, b) => b.recordDate.localeCompare(a.recordDate)).slice(0, RECENT_RECORDS_COUNT),
    [records.data],
  );

  return (
    <>
      <PageHeader title="Atopass" right={<AccountMenu />} />

      <div className="flex flex-col gap-4 px-5 pt-5">
        <section>
          <p className="text-sm text-gray-400">{formatKoreanDate(todayString)}</p>
          <p className="mt-1 text-2xl font-bold">
            {me.data ? `${me.data.nickname}님, 오늘 피부는 어땠나요?` : '오늘 피부는 어땠나요?'}
          </p>
          <p className="mt-1.5 text-sm text-gray-500">오늘의 피부 상태와 생활 습관을 기록하고 변화를 살펴보세요</p>
        </section>

        <VisitCard />

        <WeatherCard />

        {records.loading ? (
          <LoadingMessage />
        ) : records.error ? (
          <ErrorMessage message={getErrorMessage(records.error)} onRetry={records.reload} />
        ) : (
          <>
            <section className="rounded-3xl bg-white p-5 shadow-card">
              <h2 className="mb-3 font-bold text-gray-700">오늘 기록</h2>
              {todayRecord ? (
                <>
                  <p className="flex items-center gap-2">
                    <span className="rounded-full bg-brand-50 px-3 py-1 text-sm font-bold text-brand-700">기록됨</span>
                    <span className="text-gray-600">
                      {todayScore === null ? '고통 지수 기록 없음' : `고통 지수 ${todayScore}점`}
                    </span>
                  </p>
                  <Link
                    to={recordPath(todayString)}
                    className="mt-3 block rounded-2xl border border-gray-200 py-3 text-center font-medium text-gray-700"
                  >
                    오늘 총정리 보기
                  </Link>
                </>
              ) : (
                <>
                  <p className="flex items-center gap-2">
                    <span className="rounded-full bg-gray-100 px-3 py-1 text-sm font-bold text-gray-500">미기록</span>
                    <span className="text-gray-600">오늘 피부 상태를 남겨 주세요</span>
                  </p>
                  <Link
                    to="/chatbot"
                    className="mt-3 block rounded-3xl bg-brand-600 py-4 text-center text-lg font-bold text-white shadow-[0_8px_20px_rgba(76,116,224,0.28)]"
                  >
                    피부 상태 기록하기
                  </Link>
                </>
              )}
            </section>

            <section className="rounded-3xl bg-white p-5 shadow-card">
              <div className="mb-1 flex items-center justify-between">
                <h2 className="font-bold text-gray-700">최근 7일 기록</h2>
                <span className="text-sm font-bold text-brand-600">{recordedDaysCount}일 기록함</span>
              </div>
              <div className="mt-3 flex justify-between">
                {chartData.map((day) => (
                  <div key={day.date} className="flex flex-col items-center gap-1.5">
                    <span className={`size-2.5 rounded-full ${day.score !== null ? 'bg-brand-500' : 'bg-gray-200'}`} />
                    <span className="text-xs text-gray-400">{day.label}</span>
                  </div>
                ))}
              </div>
            </section>

            <section className="rounded-3xl bg-white p-5 shadow-card">
              <h2 className="font-bold text-gray-700">피부 변화</h2>
              <p className="mb-3 text-sm text-gray-400">그날 기록한 고통 지수예요</p>
              {hasAnyScore ? (
                <WeeklyScoreChart data={chartData} />
              ) : (
                <p className="py-8 text-center text-gray-400">기록이 쌓이면 변화를 확인할 수 있어요</p>
              )}
            </section>

            <section className="rounded-3xl bg-white p-5 shadow-card">
              <div className="mb-1 flex items-center justify-between">
                <h2 className="font-bold text-gray-700">최근 기록</h2>
                <Link to="/calendar" className="text-sm font-bold text-brand-600">
                  전체 보기
                </Link>
              </div>
              {recentRecords.length === 0 ? (
                <p className="py-6 text-center text-sm text-gray-400">아직 기록이 없어요. 오늘 첫 기록을 남겨 보세요.</p>
              ) : (
                <ul className="mt-2 flex flex-col divide-y divide-gray-100">
                  {recentRecords.map((record) => (
                    <li key={record.recordId}>
                      <Link to={recordPath(record.recordDate)} className="flex items-center justify-between py-3">
                        <span>
                          <span className="block font-medium">{formatKoreanDate(record.recordDate)}</span>
                          <span className="block text-sm text-gray-400">
                            {record.painIndex === null ? '고통 지수 기록 없음' : `고통 지수 ${record.painIndex}점`}
                          </span>
                        </span>
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} className="size-4 text-gray-300">
                          <path d="M9 6l6 6-6 6" strokeLinecap="round" strokeLinejoin="round" />
                        </svg>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <div className="grid grid-cols-2 gap-3">
              <Link to="/calendar" className="rounded-2xl bg-white p-4 shadow-card">
                <p className="font-bold">지난 기록 보기</p>
                <p className="mt-1 text-sm text-gray-500">날짜별 기록 확인</p>
              </Link>
              <Link to="/report" className="rounded-2xl bg-white p-4 shadow-card">
                <p className="font-bold">진료 참고용 기록</p>
                <p className="mt-1 text-sm text-gray-500">PDF로 정리하기</p>
              </Link>
            </div>
          </>
        )}
      </div>

      <Disclaimer />
    </>
  );
}
