import { useMemo } from 'react';
import { Link, useNavigate } from 'react-router';
import { authApi } from '@/apis/auth';
import { memberApi } from '@/apis/member';
import { getAllRecords } from '@/apis/recordList';
import Disclaimer from '@/components/common/Disclaimer';
import PageHeader from '@/components/common/PageHeader';
import { ErrorMessage, LoadingMessage } from '@/components/common/StatusMessage';
import { useAsync } from '@/hooks/useAsync';
import { formatKoreanDate, today } from '@/utils/date';
import { eachDay, formatMonthDay, lastNDaysRange } from '@/utils/dateRange';
import { getErrorMessage } from '@/utils/errorMessage';
import { representativeScore } from '@/utils/recordScore';
import { recordPath } from '@/utils/routes';
import WeatherCard from './WeatherCard';
import WeeklyScoreChart, { type DailyScore } from './WeeklyScoreChart';

const CHART_DAYS = 7;

export default function HomePage() {
  const navigate = useNavigate();
  const todayString = today();
  const range = useMemo(() => lastNDaysRange(CHART_DAYS), []);

  const me = useAsync(memberApi.getMe, []);
  // 오늘을 포함한 7일 기록: 오늘 기록 여부와 차트에 함께 쓴다.
  const records = useAsync(() => getAllRecords(range.startDate, range.endDate), [range]);

  const todayRecord = records.data?.find((record) => record.recordDate === todayString);
  const todayScore = representativeScore(todayRecord?.skinConditions);

  const chartData = useMemo<DailyScore[]>(() => {
    const byDate = new Map((records.data ?? []).map((record) => [record.recordDate, record]));
    return eachDay(range.startDate, range.endDate).map((date) => ({
      date,
      label: date === todayString ? '오늘' : formatMonthDay(date),
      score: representativeScore(byDate.get(date)?.skinConditions),
    }));
  }, [records.data, range, todayString]);

  const hasAnyScore = chartData.some((day) => day.score !== null);

  const handleLogout = async () => {
    await authApi.logout().catch(() => undefined);
    navigate('/login', { replace: true });
  };

  return (
    <>
      <PageHeader
        title="홈"
        right={
          <button type="button" onClick={handleLogout} className="rounded-lg px-2 py-1 text-gray-500">
            로그아웃
          </button>
        }
      />

      <div className="flex flex-col gap-4 px-4 pt-4">
        <section>
          <p className="text-gray-500">{formatKoreanDate(todayString)}</p>
          <p className="mt-1 text-xl font-bold">{me.data ? `${me.data.nickname}님, 안녕하세요` : '안녕하세요'}</p>
        </section>

        <WeatherCard />

        {records.loading ? (
          <LoadingMessage />
        ) : records.error ? (
          <ErrorMessage message={getErrorMessage(records.error)} onRetry={records.reload} />
        ) : (
          <>
            <section className="rounded-2xl border border-gray-200 p-4">
              <h2 className="mb-3 font-bold text-gray-700">오늘 기록</h2>
              {todayRecord ? (
                <>
                  <p className="flex items-center gap-2">
                    <span className="rounded-full bg-emerald-50 px-3 py-1 text-sm font-bold text-emerald-700">기록됨</span>
                    <span className="text-gray-600">
                      {todayScore === null ? '증상 점수 기록 없음' : `가장 높은 증상 점수 ${todayScore}점`}
                    </span>
                  </p>
                  <Link
                    to={recordPath(todayString)}
                    className="mt-3 block rounded-xl border border-gray-300 py-3 text-center"
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
                    className="mt-3 block rounded-xl bg-emerald-600 py-4 text-center text-lg font-bold text-white"
                  >
                    오늘 기록하기
                  </Link>
                </>
              )}
            </section>

            <section className="rounded-2xl border border-gray-200 p-4">
              <h2 className="font-bold text-gray-700">최근 7일 증상 점수</h2>
              <p className="mb-3 text-sm text-gray-400">그날 기록한 증상 중 가장 높은 점수예요.</p>
              {hasAnyScore ? (
                <WeeklyScoreChart data={chartData} />
              ) : (
                <p className="py-8 text-center text-gray-400">최근 7일 동안 기록이 없어요.</p>
              )}
            </section>

            <div className="grid grid-cols-2 gap-3">
              <Link to="/calendar" className="rounded-2xl border border-gray-200 p-4">
                <p className="font-bold">지난 기록 보기</p>
                <p className="mt-1 text-sm text-gray-500">날짜별 기록 확인</p>
              </Link>
              <Link to="/report" className="rounded-2xl border border-gray-200 p-4">
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
