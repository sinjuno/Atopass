import { useState } from 'react';
import { medicationApi } from '@/apis/medication';
import AccountMenu from '@/components/common/AccountMenu';
import PageHeader from '@/components/common/PageHeader';
import { ErrorMessage, LoadingMessage } from '@/components/common/StatusMessage';
import MedicationCard from '@/components/medication/MedicationCard';
import MedicationForm from '@/components/medication/MedicationForm';
import MedicationPhotoFlow from '@/components/medication/MedicationPhotoFlow';
import { useAsync } from '@/hooks/useAsync';
import type { Medication, MedicationInput } from '@/types/medication';
import { getErrorMessage } from '@/utils/errorMessage';
import { changedFields, medicationErrorMessage } from '@/utils/medication';

type View = { kind: 'list' } | { kind: 'photo' } | { kind: 'add' } | { kind: 'edit'; medication: Medication };

/** 내 약 관리 — /medications */
export default function MedicationsPage() {
  const active = useAsync(() => medicationApi.list('active'), []);
  const stopped = useAsync(() => medicationApi.list('stopped'), []);
  const [view, setView] = useState<View>({ kind: 'list' });
  const [formError, setFormError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const reloadAll = () => {
    active.reload();
    stopped.reload();
  };
  const backToList = () => {
    setView({ kind: 'list' });
    setFormError(null);
  };

  const handleAdd = async (values: MedicationInput) => {
    setFormError(null);
    try {
      await medicationApi.create(values, 'mypage');
      setNotice('약을 등록했어요.');
      reloadAll();
      backToList();
    } catch (e) {
      setFormError(medicationErrorMessage(e));
    }
  };

  const handleEdit = (before: Medication) => async (values: MedicationInput) => {
    setFormError(null);
    const patch = changedFields(before, values);
    try {
      if (Object.keys(patch).length > 0) await medicationApi.update(before.id, patch);
      setNotice('수정했어요.');
      reloadAll();
      backToList();
    } catch (e) {
      setFormError(medicationErrorMessage(e));
    }
  };

  const handleStop = (id: string) => async (endDate: string) => {
    try {
      await medicationApi.stop(id, endDate);
      setNotice('복용 중단으로 옮겼어요.');
      reloadAll();
    } catch (e) {
      setNotice(medicationErrorMessage(e));
    }
  };

  if (view.kind !== 'list') {
    const title = view.kind === 'photo' ? '사진으로 등록' : view.kind === 'add' ? '약 직접 입력' : '약 수정';
    return (
      <>
        <PageHeader title={title} />
        <div className="px-5 pt-4 pb-6">
          {view.kind === 'photo' && (
            <MedicationPhotoFlow
              onSaved={(count) => {
                setNotice(`${count}개의 약을 등록했어요.`);
                reloadAll();
                backToList();
              }}
              onCancel={backToList}
              onManual={() => setView({ kind: 'add' })}
            />
          )}
          {view.kind === 'add' && <MedicationForm onSubmit={handleAdd} onCancel={backToList} error={formError} />}
          {view.kind === 'edit' && (
            <MedicationForm
              initial={view.medication}
              onSubmit={handleEdit(view.medication)}
              onCancel={backToList}
              error={formError}
            />
          )}
        </div>
      </>
    );
  }

  return (
    <>
      <PageHeader title="내 약 관리" right={<AccountMenu />} />
      <div className="flex flex-col gap-4 px-5 pt-4 pb-6">
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => {
              setNotice(null);
              setView({ kind: 'photo' });
            }}
            className="flex-1 rounded-2xl bg-brand-600 py-3 font-bold text-white"
          >
            📷 복약 안내문 찍어서 등록
          </button>
          <button
            type="button"
            onClick={() => {
              setNotice(null);
              setView({ kind: 'add' });
            }}
            className="flex-1 rounded-2xl border border-gray-200 bg-white py-3 font-bold text-gray-700"
          >
            직접 입력
          </button>
        </div>

        {notice && (
          <p role="status" className="rounded-xl bg-brand-50 px-4 py-3 text-brand-700">
            {notice}
          </p>
        )}

        <section aria-label="복용 중인 약" className="flex flex-col gap-3">
          <h2 className="font-bold text-gray-700">복용 중인 약</h2>
          {active.loading ? (
            <LoadingMessage />
          ) : active.error ? (
            <ErrorMessage message={getErrorMessage(active.error)} onRetry={active.reload} />
          ) : active.data && active.data.length > 0 ? (
            <ul className="flex flex-col gap-3">
              {active.data.map((m) => (
                <MedicationCard
                  key={m.id}
                  medication={m}
                  onEdit={() => {
                    setNotice(null);
                    setView({ kind: 'edit', medication: m });
                  }}
                  onStop={handleStop(m.id)}
                />
              ))}
            </ul>
          ) : (
            <p className="rounded-3xl bg-white p-5 text-center text-gray-500 shadow-card">
              등록된 약이 없어요.
            </p>
          )}
        </section>

        <details className="rounded-3xl bg-white p-4 shadow-card">
          <summary className="cursor-pointer font-bold text-gray-700">
            복용 중단한 약{stopped.data ? ` (${stopped.data.length})` : ''}
          </summary>
          <div className="mt-3">
            {stopped.loading ? (
              <LoadingMessage />
            ) : stopped.error ? (
              <ErrorMessage message={getErrorMessage(stopped.error)} onRetry={stopped.reload} />
            ) : stopped.data && stopped.data.length > 0 ? (
              <ul className="flex flex-col gap-3">
                {stopped.data.map((m) => (
                  <MedicationCard key={m.id} medication={m} />
                ))}
              </ul>
            ) : (
              <p className="text-gray-500">중단한 약이 없어요.</p>
            )}
          </div>
        </details>
      </div>
    </>
  );
}
