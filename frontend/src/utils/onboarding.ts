import { tokenStore } from '@/utils/tokenStore';

const KEY_PREFIX = 'atopass.onboarded.';

/** 액세스 토큰(JWT)의 sub(회원 ID). 토큰이 없거나 읽지 못하면 null */
function memberId(): string | null {
  const token = tokenStore.get();
  if (!token) return null;
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const sub = (JSON.parse(atob(payload)) as { sub?: string }).sub;
    return sub ?? null;
  } catch {
    return null;
  }
}

/**
 * 이 회원이 온보딩을 이미 봤는지. 첫 로그인 뒤에 한 번만 보여 주려고 회원마다 따로 기억한다.
 * 회원을 알 수 없거나 저장소를 못 쓰는 환경이면 본 것으로 쳐서, 온보딩이 반복해서 막지 않게 한다.
 */
export function hasSeenOnboarding(): boolean {
  const id = memberId();
  if (!id) return true;
  try {
    return localStorage.getItem(KEY_PREFIX + id) === '1';
  } catch {
    return true;
  }
}

export function markOnboardingSeen(): void {
  const id = memberId();
  if (!id) return;
  try {
    localStorage.setItem(KEY_PREFIX + id, '1');
  } catch {
    // 저장하지 못해도 온보딩을 한 번 더 볼 뿐이다
  }
}
