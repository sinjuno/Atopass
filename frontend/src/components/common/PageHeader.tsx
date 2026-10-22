import type { ReactNode } from 'react';

export default function PageHeader({ title, right }: { title: string; right?: ReactNode }) {
  return (
    <header className="sticky top-0 z-10 flex items-center justify-between bg-white px-5 py-4 shadow-sm">
      <h1 className="text-xl font-bold">{title}</h1>
      {right}
    </header>
  );
}
