import { useState, type FormEvent } from 'react';
import { reportApi } from '@/apis/report';
import Disclaimer from '@/components/common/Disclaimer';
import PageHeader from '@/components/common/PageHeader';
import ReportPreview from '@/components/report/ReportPreview';
import { useAsync } from '@/hooks/useAsync';
import type { Report } from '@/types/report';
import { toDateString, today } from '@/utils/date';
import { formatFullDate, lastNDaysRange } from '@/utils/dateRange';
import { getErrorMessage, getFieldErrors } from '@/utils/errorMessage';

const QUICK_RANGES = [
  { days: 14, label: '2주' },
  { days: 30, label: '1개월' },
  { days: 90, label: '3개월' },
];

/** 사진 외 항목은 API(POST /reports)가 선택을 받지 않아 항상 포함된다 (화면 명세서 4-3 미정) */
const FIXED_ITEMS = ['부위별 증상 변화', '수면', '특이사항'];

export default function ReportPage() {
  const todayString = today();
  const [range, setRange] = useState(() => lastNDaysRange(30));
  const [includePhotos, setIncludePhotos] = useState(false); // 명세 기본값 false
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [selected, setSelected] = useState<Report | null>(null);

  const reports = useAsync(reportApi.getList, []);

  const rangeError =
    range.startDate > range.endDate
      ? '시작일이 종료일보다 늦어요.'
      : range.endDate > todayString
        ? '종료일은 오늘 이후로 선택할 수 없어요.'
        : null;

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (submitting || rangeError) return;

    setSubmitting(true);
    setFormError(null);
    try {
      // PDF 는 서버에서 동기로 생성된다. 생성 직후 파일을 불러와 미리보기한다.
      const report = await reportApi.create({ ...range, includePhotos });
      setSelected(report);
      reports.reload();
    } catch (error) {
      setFormError(Object.values(getFieldErrors(error))[0] ?? getErrorMessage(error));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <PageHeader title="진료 참고용 기록" />

      <div className="flex flex-col gap-4 px-4 pt-4">
        <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-5 rounded-2xl border border-gray-200 p-4">
          <fieldset>
            <legend className="mb-2 font-bold text-gray-700">기간</legend>
            <div className="mb-3 flex flex-wrap gap-1.5">
              {QUICK_RANGES.map((quick) => {
                const quickRange = lastNDaysRange(quick.days);
                const active = quickRange.startDate === range.startDate && quickRange.endDate === range.endDate;
                return (
                  <button
                    key={quick.days}
                    type="button"
                    aria-pressed={active}
                    onClick={() => setRange(quickRange)}
                    className={`rounded-full px-3 py-1.5 text-sm font-medium ${
                      active ? 'bg-emerald-600 text-white' : 'bg-gray-100 text-gray-600'
                    }`}
                  >
                    최근 {quick.label}
                  </button>
                );
              })}
            </div>
            <div className="flex items-center gap-2">
              <input
                type="date"
                aria-label="시작일"
                value={range.startDate}
                max={todayString}
                onChange={(e) => e.target.value && setRange((prev) => ({ ...prev, startDate: e.target.value }))}
                className="min-w-0 flex-1 rounded-xl border border-gray-300 px-3 py-2.5"
              />
              <span className="text-gray-400">~</span>
              <input
                type="date"
                aria-label="종료일"
                value={range.endDate}
                max={todayString}
                onChange={(e) => e.target.value && setRange((prev) => ({ ...prev, endDate: e.target.value }))}
                className="min-w-0 flex-1 rounded-xl border border-gray-300 px-3 py-2.5"
              />
            </div>
            {rangeError && <p className="mt-1.5 text-sm text-red-600">{rangeError}</p>}
          </fieldset>

          <fieldset>
            <legend className="mb-2 font-bold text-gray-700">포함 항목</legend>
            <ul className="flex flex-col gap-2">
              {FIXED_ITEMS.map((item) => (
                <li key={item}>
                  <label className="flex items-center gap-2.5 text-gray-500">
                    <input type="checkbox" checked disabled className="size-5 accent-emerald-600" />
                    {item}
                  </label>
                </li>
              ))}
              <li>
                <label className="flex items-center gap-2.5">
                  <input
                    type="checkbox"
                    checked={includePhotos}
                    onChange={(e) => setIncludePhotos(e.target.checked)}
                    className="size-5 accent-emerald-600"
                  />
                  사진
                </label>
              </li>
            </ul>
            <p className="mt-2 text-sm text-gray-400">사진 외 항목은 현재 항상 포함돼요.</p>
          </fieldset>

          {formError && (
            <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-red-700">
              {formError}
            </p>
          )}

          <button
            type="submit"
            disabled={submitting || !!rangeError}
            className="rounded-xl bg-emerald-600 py-4 text-lg font-bold text-white disabled:opacity-60"
          >
            {submitting ? 'PDF 만드는 중…' : 'PDF 만들기'}
          </button>
        </form>

        {selected && (
          <section id="report-preview" className="scroll-mt-16 rounded-2xl border border-gray-200 p-4">
            <h2 className="mb-3 font-bold text-gray-700">미리보기</h2>
            <ReportPreview key={selected.reportId} report={selected} />
          </section>
        )}

        <section className="rounded-2xl border border-gray-200 p-4">
          <h2 className="mb-2 font-bold text-gray-700">이전에 만든 기록</h2>
          {reports.loading ? (
            <p className="py-4 text-center text-gray-400">불러오는 중…</p>
          ) : reports.error ? (
            <p className="py-4 text-center text-gray-400">
              목록을 불러오지 못했어요.{' '}
              <button type="button" onClick={reports.reload} className="font-medium text-emerald-600 underline">
                다시 시도
              </button>
            </p>
          ) : !reports.data?.length ? (
            <p className="py-4 text-center text-gray-400">아직 만든 기록이 없어요.</p>
          ) : (
            <ul className="divide-y divide-gray-100">
              {[...reports.data]
                .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
                .map((report) => (
                  <li key={report.reportId}>
                    <button
                      type="button"
                      onClick={() => {
                        setSelected(report);
                        // 목록 위에 있는 미리보기로 스크롤한다.
                        requestAnimationFrame(() =>
                          document.getElementById('report-preview')?.scrollIntoView({ behavior: 'smooth' }),
                        );
                      }}
                      aria-current={selected?.reportId === report.reportId}
                      className="flex w-full items-center justify-between gap-3 py-3 text-left"
                    >
                      <span>
                        <span className="block font-medium">
                          {formatFullDate(report.startDate)} ~ {formatFullDate(report.endDate)}
                        </span>
                        <span className="mt-0.5 block text-sm text-gray-400">
                          {formatFullDate(toDateString(new Date(report.createdAt)))} 생성
                          {report.includePhotos ? ' · 사진 포함' : ''}
                        </span>
                      </span>
                      <span className="shrink-0 font-bold text-emerald-600">보기</span>
                    </button>
                  </li>
                ))}
            </ul>
          )}
        </section>
      </div>

      <Disclaimer />
    </>
  );
}
