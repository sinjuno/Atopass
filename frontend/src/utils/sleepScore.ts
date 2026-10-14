import { SLEEP_SCORE_MAX } from './constants';

const OPTIMAL_MIN = 7;
const OPTIMAL_MAX = 9;
const STEP_HOURS = 2;

/**
 * 수면 점수(1~5) — 서버(SleepScore)와 같은 규칙.
 * 7~9시간이 5점, 그 범위에서 2시간 벗어날 때마다 1점씩 깎는다(0.1시간만 벗어나도 1점 감점).
 */
export function sleepScore(hours: number | null | undefined): number | null {
  if (hours == null || Number.isNaN(hours)) return null;
  const gap = hours < OPTIMAL_MIN ? OPTIMAL_MIN - hours : hours > OPTIMAL_MAX ? hours - OPTIMAL_MAX : 0;
  // 부동소수 오차(예: 7 - 6.9)로 올림이 한 칸 더 되지 않도록 소수 첫째 자리에서 맞춘다
  const penalty = Math.ceil(Math.round((gap / STEP_HOURS) * 100) / 100);
  return Math.max(1, SLEEP_SCORE_MAX - penalty);
}
