import { toDateString } from './date';

export const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

/** 고통 지수 구간별 표시 색 (캘린더 점) */
export const SCORE_LEVELS = [
  { max: 3, label: '0~3점', className: 'bg-emerald-500' },
  { max: 6, label: '4~6점', className: 'bg-amber-400' },
  { max: 10, label: '7~10점', className: 'bg-red-500' },
];

/** 그날 기록의 점 색. 고통 지수가 없으면 회색 */
export const scoreColor = (score: number | null) =>
  score === null ? 'bg-gray-400' : SCORE_LEVELS.find((level) => score <= level.max)!.className;

/** 해당 달의 1일 ~ 말일 (yyyy-MM-dd) */
export function monthRange(year: number, month: number) {
  return { startDate: toDateString(new Date(year, month, 1)), endDate: toDateString(new Date(year, month + 1, 0)) };
}

/** 달력 칸: 앞쪽 빈칸(null) + 1일부터 말일까지 */
export function monthCells(year: number, month: number): (string | null)[] {
  const blanks = new Date(year, month, 1).getDay();
  const days = new Date(year, month + 1, 0).getDate();
  return [
    ...Array<null>(blanks).fill(null),
    ...Array.from({ length: days }, (_, i) => toDateString(new Date(year, month, i + 1))),
  ];
}
