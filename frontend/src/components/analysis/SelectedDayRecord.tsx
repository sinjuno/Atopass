import { Link } from 'react-router';
import { recordApi } from '@/apis/record';
import { ErrorMessage, LoadingMessage } from '@/components/common/StatusMessage';
import RecordView from '@/components/record/RecordView';
import { useAsync } from '@/hooks/useAsync';
import { formatKoreanDate } from '@/utils/date';
import { getErrorMessage } from '@/utils/errorMessage';
import { recordPath } from '@/utils/routes';

interface Props {
  date: string;
  recordId: number;
  onClose: () => void;
}

/** 그래프에서 고른 날의 기록. 화면을 옮기지 않고 그래프 바로 아래에 보여 준다 (읽기 전용, 수정은 하루 기록 화면에서) */
export default function SelectedDayRecord({ date, recordId, onClose }: Props) {
  const detail = useAsync(() => recordApi.getDetail(recordId), [recordId]);

  return (
    <section aria-label={`${formatKoreanDate(date)} 기록`} className="mt-4 border-t-8 border-gray-50">
      <div className="flex items-center justify-between px-4 pt-4">
        <h2 className="text-lg font-bold">{formatKoreanDate(date)}</h2>
        <div className="flex items-center gap-2">
          <Link
            to={recordPath(date)}
            className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-semibold text-gray-700"
          >
            수정·자세히
          </Link>
          <button type="button" onClick={onClose} className="rounded-lg px-2 py-1.5 text-sm text-gray-500">
            닫기
          </button>
        </div>
      </div>

      {detail.loading ? (
        <LoadingMessage message="기록을 불러오는 중…" />
      ) : detail.error || !detail.data ? (
        <div className="px-4 pt-4">
          <ErrorMessage message={getErrorMessage(detail.error)} onRetry={detail.reload} />
        </div>
      ) : (
        <RecordView
          record={detail.data}
          recordId={detail.data.recordId}
          photos={detail.data.photos}
          situationalAnswers={detail.data.situationalAnswers}
        />
      )}
    </section>
  );
}
