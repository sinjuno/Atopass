/** 불러오는 중 */
export function LoadingMessage({ message = '불러오는 중…' }: { message?: string }) {
  return (
    <div role="status" className="flex flex-col items-center gap-3 py-10 text-gray-500">
      <span className="size-6 animate-spin rounded-full border-2 border-brand-100 border-t-brand-600" />
      {message}
    </div>
  );
}

/** 오류 + 다시 시도 */
export function ErrorMessage({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="flex flex-col items-center gap-3 rounded-xl bg-red-50 px-4 py-6 text-center">
      <p className="text-red-700">{message}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="rounded-lg border border-red-200 bg-white px-4 py-2 font-medium text-red-700"
        >
          다시 시도
        </button>
      )}
    </div>
  );
}
