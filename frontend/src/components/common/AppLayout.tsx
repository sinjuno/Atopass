import type { ReactNode } from 'react';
import { NavLink, Outlet } from 'react-router';

function TabIcon({ children }: { children: ReactNode }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-[22px]"
      aria-hidden="true"
    >
      {children}
    </svg>
  );
}

const TABS = [
  { to: '/', label: '홈', end: true, icon: <><path d="M3.5 10.5 12 3.7l8.5 6.8" /><path d="M5.5 9.5V20h13V9.5" /><path d="M10 20v-5.5h4V20" /></> },
  { to: '/chatbot', label: '기록', icon: <><path d="M20.5 11.8a8 8 0 0 1-11.6 7.1L4 20l1-4.5a8 8 0 1 1 15.5-3.7Z" /><path d="M8.5 12h.01M12 12h.01M15.5 12h.01" /></> },
  { to: '/calendar', label: '캘린더', icon: <><rect x="3.5" y="5" width="17" height="16" rx="2" /><path d="M16 3v4M8 3v4M3.5 10h17" /></> },
  { to: '/analysis', label: '분석', icon: <><path d="M3.5 3.5v17h17" /><path d="m7 15 4-4 3 3 5-6" /></> },
  { to: '/report', label: '진료기록', icon: <><rect x="5" y="4" width="14" height="17" rx="2" /><path d="M9 4V3h6v1" /><path d="M12 9.5v6M9 12.5h6" /></> },
];

export default function AppLayout() {
  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-md flex-col bg-[#f7f7f3] md:max-w-2xl lg:max-w-3xl">
      <main className="flex-1 pb-[calc(5.5rem+env(safe-area-inset-bottom))]">
        <Outlet />
      </main>
      <nav aria-label="주요 메뉴" className="fixed inset-x-0 bottom-0 z-40 mx-auto max-w-md border-t border-[#e7e9e2] bg-white/95 pb-[env(safe-area-inset-bottom)] backdrop-blur-md md:max-w-2xl lg:max-w-3xl">
        <ul className="grid grid-cols-5">
          {TABS.map((tab) => (
            <li key={tab.to}>
              <NavLink
                to={tab.to}
                end={tab.end}
                className={({ isActive }) => `relative flex min-h-[4.25rem] flex-col items-center justify-center gap-1 px-1 py-2 text-[11px] tracking-[-0.01em] transition-colors ${isActive ? 'font-semibold text-brand-700' : 'font-medium text-[#8a9087] hover:text-[#4b5549]'}`}
              >
                {({ isActive }) => <>
                  {isActive && <span className="absolute inset-x-5 top-0 h-[2px] rounded-full bg-brand-600" />}
                  <TabIcon>{tab.icon}</TabIcon>
                  <span>{tab.label}</span>
                </>}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
    </div>
  );
}
