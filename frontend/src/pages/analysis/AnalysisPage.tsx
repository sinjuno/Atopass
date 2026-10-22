import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { analysisApi } from '@/apis/analysis';
import { getAllRecords } from '@/apis/recordList';
import { visitApi } from '@/apis/visit';
import { getHistoricalWeather } from '@/apis/weather';
import OverlayTrendChart from '@/components/analysis/OverlayTrendChart';
import PeriodInsights from '@/components/analysis/PeriodInsights';
import RangeDatePicker, { type DateRange } from '@/components/analysis/RangeDatePicker';
import TrendLineChart, { type TrendDatum } from '@/components/analysis/TrendLineChart';
import PageHeader from '@/components/common/PageHeader';
import { ErrorMessage, LoadingMessage } from '@/components/common/StatusMessage';
import { useAsync } from '@/hooks/useAsync';
import {
  HUMIDITY_MAX,
  HUMIDITY_MIN,
  HUMIDITY_TICKS,
  TEMPERATURE_MAX,
  TEMPERATURE_MIN,
  TEMPERATURE_TICKS,
} from '@/utils/constants';
import { formatKoreanDate, today } from '@/utils/date';
import { eachDay, lastNDaysRange } from '@/utils/dateRange';
import { getErrorMessage } from '@/utils/errorMessage';
import { dDayLabel, daysUntil, visitCycle } from '@/utils/visit';

type Period = '14' | '30' | 'VISIT' | 'CUSTOM';
type Tab = 'overlay' | 'weather';

const PERIODS: { value: Period; label: string }[] = [
  { value: '14', label: '14일' },
  { value: '30', label: '30일' },
  { value: 'VISIT', label: '재진 사이' },
  { value: 'CUSTOM', label: '직접 선택' },
];

const TABS: { value: Tab; label: string }[] = [
  { value: 'overlay', label: '전체' },
  { value: 'weather', label: '날씨' },
];

/**
 * 기간 필터를 날짜로 변환한다. '재진 사이'는 지난 재진 날부터 오늘까지(다음 재진 전까지 쌓인 기록)이고,
 * 지난 재진을 기록하지 않았으면 null이다. '직접 선택'은 달력에서 고른 시작일~종료일이다.
 */
function periodToRange(period: Period, lastVisit: string | null, custom: DateRange) {
  if (period === 'VISIT') return lastVisit ? { startDate: lastVisit, endDate: today() } : null;
  if (period === 'CUSTOM') return custom;
  return lastNDaysRange(Number(period));
}

/** 기록이 있는 날만 담긴 목록을 날짜별 연속 데이터로 펼친다. 기록 없는 날은 null (값을 채우지 않음). */
function toDailySeries(items: TrendDatum[], startDate: string, endDate: string) {
  const byDate = new Map(items.map((item) => [item.date, item]));
  return eachDay(startDate, endDate).map((date) => byDate.get(date) ?? { date, value: null, recordId: null });
}

export default function AnalysisPage() {
  const [period, setPeriod] = useState<Period>('30');
  const [tab, setTab] = useState<Tab>('overlay');
  // '직접 선택' 기간. 처음에는 최근 30일로 채워 둔다.
  const [custom, setCustom] = useState<DateRange>(() => lastNDaysRange(30));
  const navigate = useNavigate();

  const visits = useAsync(visitApi.getList, []);
  const { lastVisit, nextVisit } = visitCycle(visits.data ?? [], today());
  const range = useMemo(() => periodToRange(period, lastVisit, custom), [period, lastVisit, custom]);
  const isVisitPeriod = period === 'VISIT';
  const isCustomPeriod = period === 'CUSTOM';
  const isWeatherTab = tab === 'weather';
  const isOverlayTab = tab === 'overlay';

  // /analysis/trends는 증상 하나씩만 주므로(명세 4-2) 기록 목록을 가공한다.
  const records = useAsync(() => (range ? getAllRecords(range.startDate, range.endDate) : Promise.resolve([])), [range]);
  // 분석 요약('전체' 탭): 그래프를 위로 올리고, 기록 부족 안내는 그래프 아래에, 인사이트는 접어서 그 아래에 둔다.
  const summary = useAsync(() => (range ? analysisApi.getSummary(range) : Promise.resolve(null)), [range]);

  const needsWeather = isWeatherTab || isOverlayTab;
  const weather = useAsync(
    () => (needsWeather && range ? getHistoricalWeather(range.startDate, range.endDate) : Promise.resolve({ locationLabel: '', points: [] })),
    [needsWeather, range],
  );
  const temperatureSeries: TrendDatum[] = useMemo(
    () => (weather.data?.points ?? []).map((p) => ({ date: p.date, value: p.temperature, recordId: null })),
    [weather.data],
  );
  const humiditySeries: TrendDatum[] = useMemo(
    () => (weather.data?.points ?? []).map((p) => ({ date: p.date, value: p.humidity, recordId: null })),
    [weather.data],
  );
  const hasWeather = temperatureSeries.some((d) => d.value !== null) || humiditySeries.some((d) => d.value !== null);

  // '전체' 탭용 고통 지수·수면 시간(날씨와 같은 날짜 범위를 써서 날짜가 맞물리게 한다).
  const overlayScoreSeries = useMemo(() => {
    if (!range) return [];
    const items = (records.data ?? []).map((r) => ({
      date: r.recordDate,
      value: r.painIndex ?? null,
      recordId: r.recordId,
    }));
    return toDailySeries(items, range.startDate, range.endDate);
  }, [records.data, range]);
  // 그래프에서 기록이 있는 날을 누르면 그날 기록 화면으로 넘어간다.
  const openRecord = (date: string) => {
    if (records.data?.some((r) => r.recordDate === date)) navigate(`/record/${date}`);
  };
  const overlaySleepSeries = useMemo(() => {
    if (!range) return [];
    const items = (records.data ?? []).map((r) => ({ date: r.recordDate, value: r.sleepHours ?? null, recordId: r.recordId }));
    return toDailySeries(items, range.startDate, range.endDate);
  }, [records.data, range]);

  return (
    <>
      <PageHeader title="피부 변화" />

      <div className="flex flex-col gap-4 px-5 pt-4 pb-4">
        <SegmentedControl options={PERIODS} value={period} onChange={setPeriod} ariaLabel="기간" />
        {isVisitPeriod && lastVisit && (
          <p className="-mt-2 text-sm text-gray-500">
            지난 재진 {formatKoreanDate(lastVisit)}부터 오늘까지
            {nextVisit && ` · 다음 재진 ${formatKoreanDate(nextVisit)} (${dDayLabel(daysUntil(nextVisit, today()))})`}
          </p>
        )}
        {isCustomPeriod && <RangeDatePicker value={custom} onChange={setCustom} />}

        <section className="rounded-3xl bg-white p-5 shadow-card">
          <div
            role="tablist"
            aria-label="그래프 종류"
            className="mb-4 flex gap-1 overflow-x-auto border-b border-gray-100"
          >
            {TABS.map((t) => (
              <button
                key={t.value}
                type="button"
                role="tab"
                aria-selected={tab === t.value}
                onClick={() => setTab(t.value)}
                className={`-mb-px shrink-0 whitespace-nowrap border-b-2 px-3 pb-2 font-bold ${
                  tab === t.value ? 'border-brand-600 text-brand-700' : 'border-transparent text-gray-400'
                }`}
              >
                {t.label}
              </button>
            ))}
          </div>

          {isWeatherTab && (
            <p className="-mt-1 mb-3 text-sm text-gray-400">
              하루 평균 기온·습도예요.{weather.data?.locationLabel && ` (${weather.data.locationLabel} 기준)`}
            </p>
          )}

          {isVisitPeriod && !range ? (
            visits.loading ? (
              <LoadingMessage />
            ) : visits.error ? (
              <ErrorMessage message={getErrorMessage(visits.error)} onRetry={visits.reload} />
            ) : (
              <p className="py-12 text-center text-gray-400">
                홈에서 지난 재진 날짜를 기록하면
                <br />
                재진 사이 기록을 볼 수 있어요.
              </p>
            )
          ) : isOverlayTab ? (
            weather.loading || records.loading ? (
              <LoadingMessage />
            ) : weather.error ? (
              <ErrorMessage message={getErrorMessage(weather.error)} onRetry={weather.reload} />
            ) : records.error ? (
              <ErrorMessage message={getErrorMessage(records.error)} onRetry={records.reload} />
            ) : !records.data?.length ? (
              <p className="py-12 text-center text-gray-400">이 기간에 기록이 없어요.</p>
            ) : (
              <OverlayTrendChart
                key={period}
                pain={overlayScoreSeries}
                aux={{ sleep: overlaySleepSeries, temperature: temperatureSeries, humidity: humiditySeries }}
                onDateClick={openRecord}
              />
            )
          ) : (
            weather.loading ? (
              <LoadingMessage />
            ) : weather.error ? (
              <ErrorMessage message={getErrorMessage(weather.error)} onRetry={weather.reload} />
            ) : !hasWeather ? (
              <p className="py-12 text-center text-gray-400">이 기간의 날씨 정보를 가져오지 못했어요.</p>
            ) : (
              <div className="flex flex-col gap-6">
                <div>
                  <p className="mb-2 text-sm font-medium text-gray-500">온도</p>
                  <TrendLineChart
                    data={temperatureSeries}
                    domain={[TEMPERATURE_MIN, TEMPERATURE_MAX]}
                    ticks={TEMPERATURE_TICKS}
                    formatValue={(v) => `평균 기온 ${v}°C`}
                    ariaLabel="기온 변화 그래프"
                  />
                </div>
                <div>
                  <p className="mb-2 text-sm font-medium text-gray-500">습도</p>
                  <TrendLineChart
                    data={humiditySeries}
                    domain={[HUMIDITY_MIN, HUMIDITY_MAX]}
                    ticks={HUMIDITY_TICKS}
                    formatValue={(v) => `평균 습도 ${v}%`}
                    ariaLabel="습도 변화 그래프"
                  />
                </div>
              </div>
            )
          )}

          {isOverlayTab && summary.data && !summary.data.availableForComparison && (
            <p className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800">
              아직 기록이 부족해요 (이 기간 {summary.data.recordCount}일). 7일 이상 기록하면 피부 변화를 비교해 드릴게요.
            </p>
          )}
        </section>

        {isOverlayTab && summary.data?.availableForComparison && (
          <PeriodInsights key={period} insights={summary.data.insights} />
        )}
      </div>
    </>
  );
}

function SegmentedControl<T extends string>({
  options,
  value,
  onChange,
  ariaLabel,
}: {
  options: { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
  ariaLabel: string;
}) {
  return (
    <div role="radiogroup" aria-label={ariaLabel} className="grid grid-cols-4 rounded-xl bg-gray-100 p-1">
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          role="radio"
          aria-checked={value === option.value}
          onClick={() => onChange(option.value)}
          className={`rounded-lg py-2.5 font-bold ${
            value === option.value ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500'
          }`}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
