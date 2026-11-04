import { useState, type ReactNode } from 'react';
import { medicationApi } from '@/apis/medication';
import { ErrorMessage, LoadingMessage } from '@/components/common/StatusMessage';
import MedicationCard from '@/components/medication/MedicationCard';
import MedicationForm from '@/components/medication/MedicationForm';
import { useAsync } from '@/hooks/useAsync';
import type { Medication, MedicationInput } from '@/types/medication';
import { getErrorMessage } from '@/utils/errorMessage';
import { changedFields, medicationErrorMessage } from '@/utils/medication';

type View = { kind: 'list' } | { kind: 'add' } | { kind: 'edit'; medication: Medication };

interface Props {
  /** 패널을 닫는다. changed 는 이번에 약 목록을 바꿨는지 */
  onClose: (changed: boolean) => void;
}

/**
 * 재진 날 처방·연고가 바뀌었다고 답했을 때, 챗봇 안에서 내 약에도 반영하는 패널.
 * 새 약 추가·복용 중단·기존 약 수정을 할 수 있고, 약 목록은 사용자가 저장(또는 복용 중단)을 직접 누른 뒤에만 바뀐다.
 * 복용 중단은 삭제하지 않고 중단일을 기록한다. 이 패널은 약에 대한 판단이나 권유는 하지 않는다.
 */
export default function ChatMedicationPanel({ onClose }: Props) {
  const active = useAsync(() => medicationApi.list('active'), []);
  const [view, setView] = useState<View>({ kind: 'list' });
  const [notice, setNotice] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [changed, setChanged] = useState(false);

  const backToList = () => {
    setView({ kind: 'list' });
    setFormError(null);
  };

  const handleAdd = async (values: MedicationInput) => {
    setFormError(null);
    try {
      await medicationApi.create(values, 'revisit');
      setChanged(true);
      setNotice('새 약을 내 약에 추가했어요.');
      active.reload();
      backToList();
    } catch (e) {
      setFormError(medicationErrorMessage(e));
    }
  };

  const handleEdit = (before: Medication) => async (values: MedicationInput) => {
    setFormError(null);
    const patch = changedFields(before, values);
    try {
      if (Object.keys(patch).length > 0) {
        await medicationApi.update(before.id, patch);
        setChanged(true);
        setNotice('내 약을 수정했어요.');
        active.reload();
      }
      backToList();
    } catch (e) {
      setFormError(medicationErrorMessage(e));
    }
  };

  const handleStop = (id: string) => async (endDate: string) => {
    try {
      await medicationApi.stop(id, endDate);
      setChanged(true);
      setNotice('복용 중단으로 옮겼어요.');
      active.reload();
    } catch (e) {
      setNotice(medicationErrorMessage(e));
    }
  };

  if (view.kind !== 'list') {
    return (
      <PanelFrame>
        <p className="mb-3 font-bold text-gray-800">{view.kind === 'add' ? '새 약 추가' : '약 수정'}</p>
        <MedicationForm
          initial={view.kind === 'edit' ? view.medication : undefined}
          submitLabel="이대로 저장할게요"
          onSubmit={view.kind === 'edit' ? handleEdit(view.medication) : handleAdd}
          onCancel={backToList}
          error={formError}
        />
      </PanelFrame>
    );
  }

  return (
    <PanelFrame>
      <p className="font-bold text-gray-800">바뀐 처방이나 연고를 내 약에도 반영할까요?</p>
      <p className="mt-1 text-sm text-gray-500">저장하거나 복용 중단을 누르기 전에는 내 약이 바뀌지 않아요.</p>

      {notice && (
        <p role="status" className="mt-3 rounded-xl bg-brand-50 px-4 py-3 text-brand-700">
          {notice}
        </p>
      )}

      <button
        type="button"
        onClick={() => {
          setNotice(null);
          setView({ kind: 'add' });
        }}
        className="mt-3 w-full rounded-2xl bg-brand-600 py-3 font-bold text-white"
      >
        + 새 약 추가
      </button>

      <div className="mt-3">
        {active.loading ? (
          <LoadingMessage />
        ) : active.error ? (
          <ErrorMessage message={getErrorMessage(active.error)} onRetry={active.reload} />
        ) : active.data && active.data.length > 0 ? (
          <>
            <p className="mb-2 text-sm font-semibold text-gray-700">복용 중인 약</p>
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
          </>
        ) : (
          <p className="text-sm text-gray-500">등록된 복용 중인 약이 없어요.</p>
        )}
      </div>

      <button
        type="button"
        onClick={() => onClose(changed)}
        className="mt-4 w-full rounded-2xl border border-gray-200 bg-white py-3 font-bold text-gray-700"
      >
        {changed ? '반영 끝냈어요' : '반영하지 않을게요'}
      </button>
    </PanelFrame>
  );
}

/** 챗봇 선택지 칸 자리에 올라오는 패널. 내용이 길어도 대화가 가려지지 않게 높이를 제한하고 안에서 스크롤한다. */
function PanelFrame({ children }: { children: ReactNode }) {
  return (
    <div className="max-h-[65dvh] overflow-y-auto border-t border-gray-200 bg-white p-4" aria-label="내 약 반영">
      {children}
    </div>
  );
}
