import type { SkinCondition } from '@/types/record';

/**
 * 그날의 대표 점수: 기록한 모든 부위·증상 중 가장 높은 점수.
 * 목록 응답에 대표 점수 필드가 없어(화면 명세서 4-4 미정) 프론트에서 계산한다.
 * 기록한 증상이 없으면 null (0으로 채우지 않는다).
 */
export function representativeScore(skinConditions: SkinCondition[] | undefined): number | null {
  const scores = (skinConditions ?? []).flatMap((condition) =>
    Object.values(condition.symptoms).filter((score): score is number => typeof score === 'number'),
  );
  return scores.length ? Math.max(...scores) : null;
}
