import type { ReactNode } from 'react';
import { NavLink, Outlet } from 'react-router';

/** 하단 탭 아이콘 (24x24 선 아이콘, 글자색을 따른다) */
function TabIcon({ children }: { children: ReactNode }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-6"
      aria-hidden="true"
    >
      {children}
    </svg>
  );
}

// 하단 탭: 홈 / 기록 / 캘린더 / 그래프 / 진료기록 (화면 명세서 1. 화면 목록)
const TABS = [
  {
    to: '/',
    label: '홈',
    end: true,
    icon: (
      <>
        <path d="M3 10.5 12 3l9 7.5" />
        <path d="M5 9.5V21h14V9.5" />
        <path d="M10 21v-6h4v6" />
      </>
    ),
  },
  {
    to: '/chatbot',
    label: '기록',
    icon: (
      <>
        <path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12Z" />
        <path d="M8.5 12h.01M12 12h.01M15.5 12h.01" />
      </>
    ),
  },
  {
    to: '/calendar',
    label: '캘린더',
    icon: (
      <>
        <rect x="3" y="5" width="18" height="16" rx="2" />
        <path d="M16 3v4M8 3v4M3 10h18" />
      </>
    ),
  },
  {
    to: '/analysis',
    label: '그래프',
    icon: (
      <>
        <path d="M3 3v18h18" />
        <path d="m7 15 4-4 3 3 5-6" />
      </>
    ),
  },
  {
    to: '/report',
    label: '진료기록',
    icon: (
      <>
        <rect x="5" y="4" width="14" height="17" rx="2" />
        <path d="M9 4V3h6v1" />
        <path d="M12 9.5v6M9 12.5h6" />
      </>
    ),
  },
];

export default function AppLayout() {
  return (
    <div className="mx-auto flex min-h-dvh max-w-md flex-col bg-white">
      <main className="flex-1 pb-20">
        <Outlet />
      </main>
      <nav className="fixed inset-x-0 bottom-0 mx-auto max-w-md border-t border-gray-200 bg-white pb-[env(safe-area-inset-bottom)]">
        <ul className="grid grid-cols-5">
          {TABS.map((tab) => (
            <li key={tab.to}>
              <NavLink
                to={tab.to}
                end={tab.end}
                className={({ isActive }) =>
                  `flex flex-col items-center gap-0.5 py-2 text-sm ${isActive ? 'font-bold text-emerald-600' : 'text-gray-500'}`
                }
              >
                <TabIcon>{tab.icon}</TabIcon>
                {tab.label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
    </div>
  );
}
