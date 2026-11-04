import { useState } from 'react';
import { useNavigate } from 'react-router';
import { medicationApi } from '@/apis/medication';
import MedicationForm from '@/components/medication/MedicationForm';
import MedicationPhotoFlow from '@/components/medication/MedicationPhotoFlow';
import { markOnboardingResolved } from '@/components/medication/OnboardingGate';
import type { MedicationInput } from '@/types/medication';
import { medicationErrorMessage } from '@/utils/medication';

type Mode = 'menu' | 'photo' | 'manual';

/** 가입 직후 약 등록 단계 — /onboarding/medications. 끝나면 홈으로 간다. */
export default function OnboardingMedicationPage() {
  const navigate = useNavigate();
  const [mode, setMode] = useState<Mode>('menu');
  const [added, setAdded] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  /** 서버에 단계를 기록한 뒤 홈으로. 기록에 실패하면 머물러 다시 시도하게 한다. */
  const finish = async (step: 'done' | 'skipped') => {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      await medicationApi.setOnboarding(step);
      markOnboardingResolved();
      navigate('/', { replace: true });
    } catch (e) {
      setError(medicationErrorMessage(e));
      setBusy(false);
    }
  };

  const handleManualSave = async (values: MedicationInput) => {
    setError(null);
    try {
      await medicationApi.create(values, 'signup');
      setAdded((n) => n + 1);
      setMode('menu');
    } catch (e) {
      setError(medicationErrorMessage(e));
    }
  };

  return (
    <div className="mx-auto flex min-h-dvh max-w-md flex-col bg-[#f5f5f5] p-6">
      <div className="my-auto rounded-3xl bg-white p-6 shadow-card">
        {mode === 'menu' && (
          <div className="flex flex-col gap-3">
            <h1 className="text-xl font-bold">지금 복용 중인 약이 있나요?</h1>
            <p className="mb-2 text-gray-500">등록해두면 진료 때 정리하기 쉬워요.</p>

            {added > 0 && (
              <p role="status" className="rounded-xl bg-brand-50 px-4 py-3 text-brand-700">
                {added}번 등록했어요. 더 있으면 이어서 등록할 수 있어요.
              </p>
            )}
            {error && (
              <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-red-700">
                {error}
              </p>
            )}

            <button
              type="button"
              onClick={() => setMode('photo')}
              className="rounded-2xl bg-brand-600 py-3.5 font-bold text-white"
            >
              📷 복약 안내문 찍어서 등록
            </button>
            <button
              type="button"
              onClick={() => setMode('manual')}
              className="rounded-2xl border border-gray-200 bg-white py-3.5 font-bold text-gray-700"
            >
              직접 입력
            </button>

            {added > 0 ? (
              <button
                type="button"
                onClick={() => void finish('done')}
                disabled={busy}
                className="rounded-2xl bg-gray-900 py-3.5 font-bold text-white disabled:opacity-40"
              >
                등록 끝내고 홈으로
              </button>
            ) : (
              <>
                <button
                  type="button"
                  onClick={() => void finish('skipped')}
                  disabled={busy}
                  className="py-2.5 text-gray-600 disabled:opacity-40"
                >
                  복용 중인 약 없어요
                </button>
                <button
                  type="button"
                  onClick={() => void finish('skipped')}
                  disabled={busy}
                  className="py-2.5 text-gray-400 disabled:opacity-40"
                >
                  나중에 할게요
                </button>
              </>
            )}
          </div>
        )}

        {mode === 'photo' && (
          <MedicationPhotoFlow
            onSaved={(count) => {
              setAdded((n) => n + count);
              setMode('menu');
            }}
            onCancel={() => setMode('menu')}
            onManual={() => setMode('manual')}
          />
        )}

        {mode === 'manual' && (
          <>
            <h1 className="mb-4 text-xl font-bold">약 직접 입력</h1>
            <MedicationForm onSubmit={handleManualSave} onCancel={() => setMode('menu')} error={error} />
          </>
        )}
      </div>
    </div>
  );
}
