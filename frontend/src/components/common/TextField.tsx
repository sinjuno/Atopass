import { useId, type InputHTMLAttributes } from 'react';

interface Props extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
  hint?: string;
}

/** 라벨 + 입력칸 + 오류/도움말 */
export default function TextField({ label, error, hint, ...inputProps }: Props) {
  const id = useId();
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;

  return (
    <div>
      <label htmlFor={id} className="mb-1.5 block font-medium text-gray-700">
        {label}
      </label>
      <input
        id={id}
        aria-invalid={!!error}
        aria-describedby={describedBy}
        className={`w-full rounded-xl border bg-white px-4 py-3 text-base outline-none focus:ring-2 ${
          error ? 'border-red-300 focus:ring-red-100' : 'border-gray-300 focus:border-emerald-500 focus:ring-emerald-100'
        }`}
        {...inputProps}
      />
      {error ? (
        <p id={`${id}-error`} className="mt-1.5 text-sm text-red-600">
          {error}
        </p>
      ) : (
        hint && (
          <p id={`${id}-hint`} className="mt-1.5 text-sm text-gray-400">
            {hint}
          </p>
        )
      )}
    </div>
  );
}
