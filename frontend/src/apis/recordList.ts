import type { RecordSummary } from '@/types/record';
import { recordApi } from './record';

const PAGE_SIZE = 100;

/** 기간 내 기록을 모든 페이지에 걸쳐 가져온다 (GET /records 는 페이지 응답, 기본 size 10). */
export async function getAllRecords(startDate: string, endDate: string): Promise<RecordSummary[]> {
  const first = await recordApi.getList({ startDate, endDate, page: 0, size: PAGE_SIZE });
  const rest = await Promise.all(
    Array.from({ length: Math.max(first.totalPages - 1, 0) }, (_, i) =>
      recordApi.getList({ startDate, endDate, page: i + 1, size: PAGE_SIZE }),
    ),
  );
  return [first, ...rest].flatMap((page) => page.content);
}
