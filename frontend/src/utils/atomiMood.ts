export type AtomiMood = 'default' | 'worried' | 'recording' | 'confused';

/** 불편함 지수가 이 점수 이상이면 아토미가 걱정하는 표정을 짓는다 */
export const WORRIED_PAIN_INDEX = 6;

/**
 * 챗봇 캐릭터 표정: 방금 말을 알아듣지 못했으면 고민하는 표정, 기록이 끝나 확인 단계면 기록하는 표정,
 * 불편함 지수가 높으면 걱정하는 표정, 그 밖에는 웃는 표정.
 * painIndex는 아직 말하지 않았으면 null이다 (0점은 말한 값).
 * understood는 가장 최근 응답에서만 false이므로, 다음 말을 알아들으면 바로 원래 표정으로 돌아온다.
 */
export function atomiMood(
  painIndex: number | null | undefined,
  requiresConfirmation: boolean,
  understood = true,
): AtomiMood {
  if (!understood) return 'confused';
  if (requiresConfirmation) return 'recording';
  if (painIndex != null && painIndex >= WORRIED_PAIN_INDEX) return 'worried';
  return 'default';
}
