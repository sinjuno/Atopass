import { useEffect, useState } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router';
import { refreshAccessToken } from '@/apis/client';
import { hasSeenOnboarding } from '@/utils/onboarding';
import { tokenStore } from '@/utils/tokenStore';

/**
 * 로그인 필요 화면 보호.
 * 액세스 토큰은 메모리에만 있어서 새로고침하면 사라지므로, 먼저 리프레시 쿠키로 재발급을 시도한다.
 * 첫 로그인 뒤에는 온보딩을 한 번 보여 준다.
 */
export default function RequireAuth() {
  const location = useLocation();
  const [state, setState] = useState<'checking' | 'ok' | 'fail'>(tokenStore.get() ? 'ok' : 'checking');

  useEffect(() => {
    if (state !== 'checking') return;
    refreshAccessToken()
      .then(() => setState('ok'))
      .catch(() => setState('fail'));
  }, [state]);

  if (state === 'checking') return <div className="p-6 text-center text-gray-500">불러오는 중…</div>;
  if (state === 'fail') return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (location.pathname !== '/onboarding' && !hasSeenOnboarding()) return <Navigate to="/onboarding" replace state={{ from: location.pathname }} />;
  return <Outlet />;
}
