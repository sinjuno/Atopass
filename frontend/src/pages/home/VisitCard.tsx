import { useState, type FormEvent } from 'react';
import { visitApi } from '@/apis/visit';
import { useAsync } from '@/hooks/useAsync';
import { formatKoreanDate, today } from '@/utils/date';
import { getErrorMessage } from '@/utils/errorMessage';
import { dDayLabel, daysUntil, visitCycle } from '@/utils/visit';

/**
 * 홈의 피부과 재진 카드. 다음 재진까지 남은 날(D-10)과 지난 재진 날짜를 보여 주고,
 * 재진 날짜를 기록하거나 잘못 넣은 날짜를 지울 수 있다.
 */
export default function VisitCard() {
  const todayString = today();
  const visits = useAsync(visitApi.getList, []);
  const [editing, setEditing] = useState(false);
  const [date, setDate] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { lastVisit, nextVisit } = visitCycle(visits.data ?? [], todayString);
  const days = nextVisit ? daysUntil(nextVisit, todayString) : null;

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!date || saving) return;
    setSaving(true);
    setError(null);
    try {
      await visitApi.create(date);
      setDate('');
      visits.reload();
    } catch (e) {
      setError(getErrorMessage(e));
    } finally {
      setSaving(false);
    }
  };

  const handleRemove = async (visitId: number) => {
    setError(null);
    try {
      await visitApi.remove(visitId);
      visits.reload();
    } catch (e) {
      setError(getErrorMessage(e));
    }
  };

  return (
    <section className="rounded-3xl bg-white p-5 shadow-card">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="font-bold text-gray-700">피부과 재진</h2>
        <button
          type="button"
          onClick={() => setEditing((v) => !v)}
          aria-expanded={editing}
          className="rounded-lg px-2 py-1 text-sm font-semibold text-brand-700"
        >
          {editing ? '닫기' : '날짜 기록'}
        </button>
      </div>

      {visits.loading && !visits.data ? (
        <p className="text-gray-400">불러오는 중…</p>
      ) : visits.error ? (
        <p className="text-gray-400">
          재진 날짜를 불러오지 못했어요.{' '}
          <button type="button" onClick={visits.reload} className="font-medium text-brand-600 underline">
            다시 시도
          </button>
        </p>
      ) : (
        <>
          {nextVisit && days !== null ? (
            <p className="flex items-center gap-3">
              <span className="rounded-xl bg-brand-600 px-3 py-1.5 text-xl font-bold text-white">{dDayLabel(days)}</span>
              <span>
                <span className="block text-sm text-gray-500">{days === 0 ? '오늘 재진이에요' : '다음 재진까지'}</span>
                <span className="block font-semibold">{formatKoreanDate(nextVisit)}</span>
              </span>
            </p>
          ) : (
            <p className="text-gray-500">다음 재진 날짜를 기록해 두면 남은 날을 알려 드려요.</p>
          )}
          {lastVisit && <p className="mt-2 text-sm text-gray-400">지난 재진 {formatKoreanDate(lastVisit)}</p>}
        </>
      )}

      {editing && (
        <div className="mt-4 flex flex-col gap-3 border-t border-gray-100 pt-4">
          <form onSubmit={handleSubmit} className="flex items-center gap-2">
            <input
              type="date"
              aria-label="재진 날짜"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              className="min-w-0 flex-1 rounded-xl border border-gray-200 px-3 py-2.5 focus:border-brand-500 focus:outline-none"
            />
            <button
              type="submit"
              disabled={!date || saving}
              className="shrink-0 rounded-xl bg-brand-600 px-4 py-2.5 font-bold text-white disabled:opacity-50"
            >
              {saving ? '저장 중…' : '저장'}
            </button>
          </form>
          <p className="text-sm text-gray-400">이미 다녀온 재진 날짜도 넣을 수 있어요. 그래프와 진료 참고용 기록에서 재진 사이 기간으로 써요.</p>
          {error && (
            <p role="alert" className="text-sm text-red-600">
              {error}
            </p>
          )}
          {!!visits.data?.length && (
            <ul className="divide-y divide-gray-100">
              {[...visits.data].reverse().map((visit) => (
                <li key={visit.visitId} className="flex items-center justify-between py-2">
                  <span className={visit.visitDate < todayString ? 'text-gray-500' : 'font-medium'}>
                    {formatKoreanDate(visit.visitDate)}
                    {visit.visitDate === todayString && ' · 오늘'}
                  </span>
                  <button
                    type="button"
                    onClick={() => handleRemove(visit.visitId)}
                    aria-label={`${formatKoreanDate(visit.visitDate)} 재진 날짜 지우기`}
                    className="rounded-lg px-2 py-1 text-sm text-gray-400"
                  >
                    지우기
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </section>
  );
}
