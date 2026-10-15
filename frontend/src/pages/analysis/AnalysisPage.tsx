import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { analysisApi } from '@/apis/analysis';
import { getAllRecords } from '@/apis/recordList';
import SleepBarChart from '@/components/analysis/SleepBarChart';
import TrendLineChart, { type TrendDatum } from '@/components/analysis/TrendLineChart';
import PageHeader from '@/components/common/PageHeader';
import { ErrorMessage, LoadingMessage } from '@/components/common/StatusMessage';
import { useAsync } from '@/hooks/useAsync';
import type { AnalysisSummary } from '@/types/analysis';
import type { BodyPart, SymptomKey } from '@/types/record';
import { BODY_PART_LABELS, SCORE_MAX, SCORE_MIN, SYMPTOM_LABELS } from '@/utils/constants';
import { today } from '@/utils/date';
import { eachDay, lastNDaysRange } from '@/utils/dateRange';
import { getErrorMessage } from '@/utils/errorMessage';
import { representativeScore } from '@/utils/recordScore';
import { recordPath } from '@/utils/routes';

type Period = '30' | '90' | 'ALL';
type Tab = 'overall' | 'bodyPart' | 'sleep';

const PERIODS: { value: Period; label: string }[] = [
  { value: '30', label: '30일' },
  { value: '90', label: '90일' },
  { value: 'ALL', label: '전체' },
];

const TABS: { value: Tab; label: string }[] = [
  { value: 'overall', label: '전체' },
  { value: 'bodyPart', label: '부위별' },
  { value: 'sleep', label: '수면·생활' },
];

const BODY_PART_OPTIONS = Object.entries(BODY_PART_LABELS).map(([value, label]) => ({ value: value as BodyPart, label }));
const SYMPTOM_OPTIONS = Object.entries(SYMPTOM_LABELS).map(([value, label]) => ({ value: value as SymptomKey, label }));

/** '전체' 기간 조회 시작일. 그래프는 첫 기록일부터 그린다. */
const ALL_START_DATE = '2000-01-01';

/** 기간 필터를 날짜로 변환한다 (화면 명세서: 30일/90일/전체는 프론트가 날짜로 변환) */
function periodToRange(period: Period) {
  if (period === 'ALL') return { startDate: ALL_START_DATE, endDate: today() };
  return lastNDaysRange(Number(period));
}

/**
 * 기록이 있는 날만 담긴 목록을 날짜별 연속 데이터로 펼친다. 기록 없는 날은 null (값을 채우지 않음).
 * '전체' 기간은 첫 기록일부터 시작한다.
 */
function toDailySeries(items: TrendDatum[], startDate: string, endDate: string, fromFirstItem: boolean) {
  const sorted = [...items].sort((a, b) => a.date.localeCompare(b.date));
  const start = fromFirstItem ? sorted[0]?.date : startDate;
  if (!start) return [];
  const byDate = new Map(sorted.map((item) => [item.date, item]));
  return eachDay(start, endDate).map((date) => byDate.get(date) ?? { date, value: null, recordId: null });
}

export default function AnalysisPage() {
  const navigate = useNavigate();
  const [period, setPeriod] = useState<Period>('30');
  const [tab, setTab] = useState<Tab>('overall');
  const [bodyPart, setBodyPart] = useState<BodyPart>('FACE');
  const [symptom, setSymptom] = useState<SymptomKey>('itching');

  const range = useMemo(() => periodToRange(period), [period]);
  const isAll = period === 'ALL';
  const isBodyPartTab = tab === 'bodyPart';

  const summary = useAsync(() => analysisApi.getSummary(range), [range]);
  // '전체'·'수면·생활' 탭은 /analysis/trends(bodyPart, symptom 필수)로 담을 수 없어 기록 목록을 가공한다 (명세 4-2 임시안)
  const records = useAsync(
    () => (isBodyPartTab ? Promise.resolve(undefined) : getAllRecords(range.startDate, range.endDate)),
    [range, isBodyPartTab],
  );
  const trends = useAsync(
    () => (isBodyPartTab ? analysisApi.getTrends({ ...range, bodyPart, symptom }) : Promise.resolve(undefined)),
    [range, isBodyPartTab, bodyPart, symptom],
  );

  const series = useMemo(() => {
    if (isBodyPartTab) {
      const points = (trends.data?.points ?? []).map((p) => ({ date: p.date, value: p.score, recordId: p.recordId }));
      return toDailySeries(points, range.startDate, range.endDate, isAll);
    }
    const items = (records.data ?? []).map((r) => ({
      date: r.recordDate,
      value: tab === 'sleep' ? (r.sleepHours ?? null) : representativeScore(r.skinConditions),
      recordId: r.recordId,
    }));
    return toDailySeries(items, range.startDate, range.endDate, isAll);
  }, [isBodyPartTab, tab, trends.data, records.data, range, isAll]);

  const active = isBodyPartTab ? trends : records;
  const hasValue = series.some((d) => d.value !== null);
  // 그래프 점을 누르면 그날의 총정리 화면으로 이동한다.
  const openRecord = (datum: TrendDatum) => navigate(recordPath(datum.date));

  return (
    <>
      <PageHeader title="피부 변화" />

      <div className="flex flex-col gap-4 px-4 pt-4">
        <SegmentedControl options={PERIODS} value={period} onChange={setPeriod} ariaLabel="기간" />

        <SummaryBox summary={summary} />

        <section className="rounded-2xl border border-gray-200 p-4">
          <div role="tablist" aria-label="그래프 종류" className="mb-4 flex gap-1 border-b border-gray-100">
            {TABS.map((t) => (
              <button
                key={t.value}
                type="button"
                role="tab"
                aria-selected={tab === t.value}
                onClick={() => setTab(t.value)}
                className={`-mb-px border-b-2 px-3 pb-2 font-bold ${
                  tab === t.value ? 'border-emerald-600 text-emerald-700' : 'border-transparent text-gray-400'
                }`}
              >
                {t.label}
              </button>
            ))}
          </div>

          {isBodyPartTab && (
            <div className="mb-4 flex flex-col gap-2">
              <ChipGroup label="부위" options={BODY_PART_OPTIONS} value={bodyPart} onChange={setBodyPart} />
              <ChipGroup label="증상" options={SYMPTOM_OPTIONS} value={symptom} onChange={setSymptom} />
            </div>
          )}
          {tab === 'overall' && <p className="-mt-1 mb-3 text-sm text-gray-400">그날 기록한 증상 중 가장 높은 점수예요.</p>}

          {active.loading ? (
            <LoadingMessage />
          ) : active.error ? (
            <ErrorMessage message={getErrorMessage(active.error)} onRetry={active.reload} />
          ) : !hasValue ? (
            <p className="py-12 text-center text-gray-400">이 기간에 기록이 없어요.</p>
          ) : tab === 'sleep' ? (
            <SleepBarChart data={series} onBarClick={openRecord} />
          ) : (
            <TrendLineChart
              data={series}
              domain={[SCORE_MIN, SCORE_MAX]}
              formatValue={(v) => (tab === 'overall' ? `가장 높은 증상 점수 ${v}점` : `증상 점수 ${v}점`)}
              onPointClick={openRecord}
              ariaLabel={tab === 'overall' ? '전체 증상 점수 변화 그래프' : '부위·증상별 점수 변화 그래프'}
            />
          )}

          {hasValue && !active.loading && !active.error && (
            <p className="mt-3 text-sm text-gray-400">그래프를 누르면 그날의 기록을 볼 수 있어요.</p>
          )}
        </section>
      </div>
    </>
  );
}

function SummaryBox({ summary }: { summary: { data?: AnalysisSummary; loading: boolean; error: unknown } }) {
  if (summary.loading || !summary.data) {
    return summary.error ? <p className="text-gray-400">분석 요약을 불러오지 못했어요.</p> : null;
  }

  const { availableForComparison, recordCount, insights } = summary.data;
  if (!availableForComparison) {
    return (
      <div className="rounded-xl bg-amber-50 px-4 py-3 text-amber-800">
        아직 기록이 부족해요 (이 기간 {recordCount}일). 7일 이상 기록하면 피부 변화를 비교해 드릴게요.
      </div>
    );
  }

  return (
    <div className="rounded-xl bg-emerald-50 px-4 py-3 text-emerald-800">
      <p className="font-bold">이 기간에 {recordCount}일 기록했어요.</p>
      {insights.length > 0 && (
        <ul className="mt-2 flex list-disc flex-col gap-1 pl-5">
          {insights.map((insight, i) => (
            <li key={`${insight.type}-${i}`}>{insight.message}</li>
          ))}
        </ul>
      )}
    </div>
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
    <div role="radiogroup" aria-label={ariaLabel} className="grid grid-cols-3 rounded-xl bg-gray-100 p-1">
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

function ChipGroup<T extends string>({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
}) {
  return (
    <div className="flex items-center gap-2">
      <span className="w-9 shrink-0 text-sm font-medium text-gray-500">{label}</span>
      <div role="radiogroup" aria-label={label} className="flex gap-1.5 overflow-x-auto pb-1">
        {options.map((option) => (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={value === option.value}
            onClick={() => onChange(option.value)}
            className={`shrink-0 rounded-full px-3 py-1.5 text-sm font-medium ${
              value === option.value ? 'bg-emerald-600 text-white' : 'bg-gray-100 text-gray-600'
            }`}
          >
            {option.label}
          </button>
        ))}
      </div>
    </div>
  );
}
