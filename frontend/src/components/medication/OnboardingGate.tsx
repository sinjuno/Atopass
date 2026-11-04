import { useEffect, useState, type ReactNode } from 'react';
import { Navigate } from 'react-router';
import { medicationApi } from '@/apis/medication';
import { LoadingMessage } from '@/components/common/StatusMessage';

// 이 세션에서 한 번 확인(또는 완료)했으면 홈에 올 때마다 다시 묻지 않는다.
let resolved = false;

export const markOnboardingResolved = () => {
  resolved = true;
};

/** 다른 계정으로 로그인할 때 다시 확인하도록 초기화 */
export const resetOnboardingGate = () => {
  resolved = false;
};

/**
 * 홈 진입 시 GET /members/me/onboarding 을 확인해서 "pending" 이면 약 등록 단계로 보낸다.
 * 확인에 실패하면 홈을 막지 않고 그대로 보여준다.
 */
export default function OnboardingGate({ children }: { children: ReactNode }) {
  const [state, setState] = useState<'checking' | 'pending' | 'ok'>(resolved ? 'ok' : 'checking');

  useEffect(() => {
    if (state !== 'checking') return;
    let cancelled = false;
    medicationApi
      .getOnboarding()
      .then((step) => {
        if (cancelled) return;
        if (step === 'pending') {
          setState('pending');
        } else {
          resolved = true;
          setState('ok');
        }
      })
      .catch(() => {
        if (!cancelled) setState('ok');
      });
    return () => {
      cancelled = true;
    };
  }, [state]);

  if (state === 'checking') return <LoadingMessage />;
  if (state === 'pending') return <Navigate to="/onboarding/medications" replace />;
  return <>{children}</>;
}
