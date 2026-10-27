/** 그래프 공통 색·글자 크기. 흰 배경에서 잘 보이도록 진한 파랑을 쓰고, 축·격자는 눈에 덜 띄게 둔다. */
export const CHART = {
  line: '#2a78d6',
  /** 선 아래 옅은 채움 (선 색 10% 정도) */
  areaOpacity: 0.12,
  /** 기록이 끊긴 구간 점선 */
  gap: '#a8b3bf',
  grid: '#e5e9ee',
  cursor: '#94a3b8',
  /** 축 글자 */
  tick: { fontSize: 12, fill: '#475569' },
  /** 가장 높은 점 옆 숫자 */
  label: '#334155',
  surface: '#ffffff',
} as const;

/** 어두운 화면(html.dark)용 축·격자 색 */
export const CHART_DARK = {
  ...CHART,
  grid: '#374151',
  cursor: '#9ca3af',
  tick: { fontSize: 12, fill: '#cbd5e1' },
  label: '#e5e7eb',
  surface: '#111827',
} as const;

export const SCORE_TICKS = [0, 2, 4, 6, 8, 10];

/** '전체' 탭의 불편함 지수 선 색 */
export const PAIN_COLOR = { light: '#E34948', dark: '#E66767' } as const;

/** '전체' 탭 보조 지표 점선 색. 불편함 지수 빨강과 헷갈리지 않는 색으로 고정한다 */
export const AUX_COLORS = {
  sleep: '#6250D6',
  temperature: '#1BAF7A',
  humidity: '#EDA100',
} as const;
