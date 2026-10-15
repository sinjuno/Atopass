import { addDays, today } from './date';

/** 오늘을 포함한 최근 n일 */
export function lastNDaysRange(n: number): { startDate: string; endDate: string } {
  const endDate = today();
  return { startDate: addDays(endDate, -(n - 1)), endDate };
}

/** startDate ~ endDate(포함) 날짜 목록 */
export function eachDay(startDate: string, endDate: string): string[] {
  const days: string[] = [];
  for (let d = startDate; d <= endDate; d = addDays(d, 1)) days.push(d);
  return days;
}

/** 2026-10-14 → 10/14 */
export function formatMonthDay(dateString: string): string {
  const [, m, d] = dateString.split('-').map(Number);
  return `${m}/${d}`;
}

/** 2026-10-14 → 2026년 10월 14일 */
export function formatFullDate(dateString: string): string {
  const [y, m, d] = dateString.split('-').map(Number);
  return `${y}년 ${m}월 ${d}일`;
}
