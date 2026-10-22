import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { authApi } from '@/apis/auth';

/** 헤더 오른쪽의 계정 메뉴. 누르면 마이페이지·로그아웃이 든 메뉴가 뜬다. 바깥을 누르거나 Esc를 누르면 닫힌다. */
export default function AccountMenu() {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false);
    };
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  const handleLogout = async () => {
    setOpen(false);
    await authApi.logout().catch(() => undefined);
    navigate('/login', { replace: true });
  };

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="메뉴"
        className="flex size-9 items-center justify-center rounded-full text-gray-500 hover:bg-gray-100"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} className="size-6" aria-hidden="true">
          <circle cx="12" cy="8" r="3.5" />
          <path d="M4.5 20c1.3-3.6 4.4-5.5 7.5-5.5s6.2 1.9 7.5 5.5" strokeLinecap="round" />
        </svg>
      </button>

      {open && (
        <div
          role="menu"
          aria-label="계정 메뉴"
          className="absolute right-0 top-11 z-20 w-44 overflow-hidden rounded-2xl bg-white py-1.5 shadow-card"
        >
          <Link
            role="menuitem"
            to="/chatbot"
            onClick={() => setOpen(false)}
            className="block px-4 py-2.5 text-base text-gray-700 hover:bg-gray-50"
          >
            기록
          </Link>
          <Link
            role="menuitem"
            to="/analysis"
            onClick={() => setOpen(false)}
            className="block px-4 py-2.5 text-base text-gray-700 hover:bg-gray-50"
          >
            그래프
          </Link>
          <Link
            role="menuitem"
            to="/mypage"
            onClick={() => setOpen(false)}
            className="block px-4 py-2.5 text-base text-gray-700 hover:bg-gray-50"
          >
            마이페이지
          </Link>
          <div role="separator" className="my-1.5 border-t border-gray-100" />
          <button
            role="menuitem"
            type="button"
            onClick={() => void handleLogout()}
            className="block w-full px-4 py-2.5 text-left text-base text-red-500 hover:bg-gray-50"
          >
            로그아웃
          </button>
        </div>
      )}
    </div>
  );
}
