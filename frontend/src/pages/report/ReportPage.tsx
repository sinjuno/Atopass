import { useState, type FormEvent, type ReactNode } from 'react';
import { reportApi } from '@/apis/report';
import { visitApi } from '@/apis/visit';
import Disclaimer from '@/components/common/Disclaimer';
import PageHeader from '@/components/common/PageHeader';
import RangeCalendar from '@/components/report/RangeCalendar';
import ReportPreview from '@/components/report/ReportPreview';
import { useAsync } from '@/hooks/useAsync';
import type { Report } from '@/types/report';
import { toDateString, today } from '@/utils/date';
import { formatFullDate, formatMonthDay, lastNDaysRange } from '@/utils/dateRange';
import { getErrorMessage, getFieldErrors } from '@/utils/errorMessage';
import { pastCycles, visitCycle } from '@/utils/visit';

/** 기간 고르는 방법: 이번 재진 주기, 지난 재진 주기(시작일로 구분), 달력에서 직접 */
type RangeChoice = 'current' | `past:${string}` | 'custom';

/** 사진 외 항목은 API(POST /reports)가 선택을 받지 않아 항상 포함된다 (화면 명세서 4-3 미정) */
const FIXED_ITEMS = ['증상이 갑자기 심해진 날', '그날·전날의 수면·음주·야외활동', '특이사항 메모'];

export default function ReportPage() {
  const todayString = today();
  // 고르기 전에는 지난 재진이 있으면 이번 재진 주기, 없으면 직접 고르기로 시작한다.
  const [choice, setChoice] = useState<RangeChoice | null>(null);
  const [customRange, setCustomRange] = useState(() => lastNDaysRange(30));
  const [includePhotos, setIncludePhotos] = useState(false); // 명세 기본값 false
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [selected, setSelected] = useState<Report | null>(null);

  const reports = useAsync(reportApi.getList, []);
  const visits = useAsync(visitApi.getList, []);

  const { lastVisit, nextVisit } = visitCycle(visits.data ?? [], todayString);
  const currentRange = lastVisit ? { startDate: lastVisit, endDate: todayString } : null;
  // 오늘이 재진 날이면 이번 주기와 같은 기간이 지난 주기에도 생기므로 뺀다.
  const cycles = pastCycles(visits.data ?? [], todayString).filter((c) => c.startDate !== currentRange?.startDate);
  const activeChoice: RangeChoice = choice ?? (currentRange ? 'current' : 'custom');
  const range =
    activeChoice === 'current' && currentRange
      ? currentRange
      : activeChoice.startsWith('past:')
        ? (cycles.find((c) => `past:${c.startDate}` === activeChoice) ?? customRange)
        : customRange;

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

      <div className="flex flex-col gap-4 px-5 pt-4 pb-4">
        <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-5 rounded-3xl bg-white p-5 shadow-card">
          <fieldset>
            <legend className="mb-2 font-bold text-gray-700">기간</legend>
            <div role="radiogroup" aria-label="기간 고르는 방법" className="mb-3 flex flex-wrap gap-1.5">
              {currentRange && (
                <RangeChip checked={activeChoice === 'current'} onClick={() => setChoice('current')}>
                  지난 재진 이후 ({formatMonthDay(currentRange.startDate)} ~ 오늘)
                </RangeChip>
              )}
              {cycles.map((cycle) => (
                <RangeChip
                  key={cycle.startDate}
                  checked={activeChoice === `past:${cycle.startDate}`}
                  onClick={() => setChoice(`past:${cycle.startDate}`)}
                >
                  재진 {formatMonthDay(cycle.startDate)} ~ {formatMonthDay(cycle.endDate)}
                </RangeChip>
              ))}
              <RangeChip checked={activeChoice === 'custom'} onClick={() => setChoice('custom')}>
                날짜 직접 고르기
              </RangeChip>
            </div>
            {!visits.loading && !currentRange && (
              <p className="mb-3 text-sm text-gray-400">홈에서 재진 날짜를 기록하면 재진 사이 기간으로 바로 만들 수 있어요.</p>
            )}
            {nextVisit && activeChoice === 'current' && (
              <p className="mb-3 text-sm text-gray-500">다음 재진({formatFullDate(nextVisit)})에 가져가기 좋아요.</p>
            )}
            {activeChoice === 'custom' && (
              <div className="mb-3 rounded-xl border border-gray-200 p-3">
                <RangeCalendar value={customRange} onChange={setCustomRange} max={todayString} />
              </div>
            )}
            <p className="font-semibold">
              {formatFullDate(range.startDate)} ~ {formatFullDate(range.endDate)}
            </p>
            {rangeError && <p className="mt-1.5 text-sm text-red-600">{rangeError}</p>}
          </fieldset>

          <fieldset>
            <legend className="mb-2 font-bold text-gray-700">포함 항목</legend>
            <ul className="flex flex-col gap-2">
              {FIXED_ITEMS.map((item) => (
                <li key={item}>
                  <label className="flex items-center gap-2.5 text-gray-500">
                    <input type="checkbox" checked disabled className="size-5 accent-brand-600" />
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
                    className="size-5 accent-brand-600"
                  />
                  사진
                </label>
              </li>
            </ul>
            <p className="mt-2 text-sm text-gray-400">
              전체를 요약하지 않고, 증상이 갑자기 심해진 날과 그 무렵 평소와 달랐던 점을 정리해요. 사진 외 항목은 항상 포함돼요.
            </p>
          </fieldset>

          {formError && (
            <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-red-700">
              {formError}
            </p>
          )}

          <button
            type="submit"
            disabled={submitting || !!rangeError}
            className="rounded-3xl bg-brand-600 py-4 text-lg font-bold text-white disabled:opacity-60"
          >
            {submitting ? 'PDF 만드는 중…' : 'PDF 만들기'}
          </button>
        </form>

        {selected && (
          <section id="report-preview" className="scroll-mt-16 rounded-3xl bg-white p-5 shadow-card">
            <h2 className="mb-3 font-bold text-gray-700">미리보기</h2>
            <ReportPreview key={selected.reportId} report={selected} />
          </section>
        )}

        <section className="rounded-3xl bg-white p-5 shadow-card">
          <h2 className="mb-2 font-bold text-gray-700">이전에 만든 기록</h2>
          {reports.loading ? (
            <p className="py-4 text-center text-gray-400">불러오는 중…</p>
          ) : reports.error ? (
            <p className="py-4 text-center text-gray-400">
              목록을 불러오지 못했어요.{' '}
              <button type="button" onClick={reports.reload} className="font-medium text-brand-600 underline">
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
                      <span className="shrink-0 font-bold text-brand-600">보기</span>
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

function RangeChip({ checked, onClick, children }: { checked: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={checked}
      onClick={onClick}
      className={`rounded-full px-3 py-1.5 text-sm font-medium ${checked ? 'bg-brand-600 text-white' : 'bg-gray-100 text-gray-600'}`}
    >
      {children}
    </button>
  );
}
