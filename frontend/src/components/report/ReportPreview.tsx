import { useEffect, useState } from 'react';
import { reportApi } from '@/apis/report';
import { ErrorMessage, LoadingMessage } from '@/components/common/StatusMessage';
import type { Report } from '@/types/report';
import { formatFullDate } from '@/utils/dateRange';
import { getErrorMessage } from '@/utils/errorMessage';

/**
 * PDF 를 Authorization 헤더로 받아(GET /reports/{reportId}/file) object URL 로 미리보기·다운로드한다.
 * 화면을 떠나거나 다른 보고서를 고르면 object URL 을 해제한다. 부모에서 key={reportId} 로 다시 마운트한다.
 */
export default function ReportPreview({ report }: { report: Report }) {
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let url: string | null = null;
    let cancelled = false;
    setError(null);
    setObjectUrl(null);

    reportApi
      .getFile(report.reportId)
      .then((blob) => {
        if (cancelled) return;
        url = URL.createObjectURL(new Blob([blob], { type: 'application/pdf' }));
        setObjectUrl(url);
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e);
      });

    return () => {
      cancelled = true;
      if (url) URL.revokeObjectURL(url);
    };
  }, [report.reportId, reloadKey]);

  const fileName = `atopass-report_${report.startDate}_${report.endDate}.pdf`;

  return (
    <div>
      <p className="mb-3 text-gray-600">
        {formatFullDate(report.startDate)} ~ {formatFullDate(report.endDate)}
        {report.includePhotos ? ' · 사진 포함' : ''}
      </p>

      {error ? (
        <ErrorMessage message={getErrorMessage(error)} onRetry={() => setReloadKey((k) => k + 1)} />
      ) : !objectUrl ? (
        <LoadingMessage message="PDF를 불러오는 중…" />
      ) : (
        <>
          <iframe
            title="진료 참고용 기록 미리보기"
            src={objectUrl}
            className="h-[28rem] w-full rounded-xl border border-gray-200 bg-gray-100"
          />
          <div className="mt-3 grid grid-cols-2 gap-2">
            <a
              href={objectUrl}
              target="_blank"
              rel="noreferrer"
              className="rounded-xl border border-gray-300 py-3 text-center font-bold text-gray-700"
            >
              새 창에서 보기
            </a>
            <a href={objectUrl} download={fileName} className="rounded-xl bg-emerald-600 py-3 text-center font-bold text-white">
              PDF 다운로드
            </a>
          </div>
        </>
      )}
    </div>
  );
}
