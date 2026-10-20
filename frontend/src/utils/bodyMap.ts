import type { BodyPart, SkinCondition, SkinSign } from '@/types/record';
import { BODY_MAP_PARTS, SKIN_SIGN_LABELS } from '@/utils/constants';

/** 기록한 마네킹 부위마다 나타난 증상 개수. 기록하지 않은 부위는 키가 없다 */
export type BodyMapCounts = Partial<Record<BodyPart, number>>;

/** 예전 부위를 마네킹 부위로 옮겨 칠할 때 쓰는 대응표 */
const LEGACY_PART_MAP: Partial<Record<BodyPart, BodyPart[]>> = {
  CHEST: ['TRUNK'],
  ABDOMEN: ['TRUNK'],
  ARM: ['RIGHT_UPPER_ARM', 'RIGHT_FOREARM', 'LEFT_UPPER_ARM', 'LEFT_FOREARM'],
  HAND: ['RIGHT_HAND', 'LEFT_HAND'],
  LEG: ['RIGHT_THIGH', 'RIGHT_LOWER_LEG', 'LEFT_THIGH', 'LEFT_LOWER_LEG'],
  FOOT: ['RIGHT_FOOT', 'LEFT_FOOT'],
};

export const isBodyMapPart = (part: BodyPart) => BODY_MAP_PARTS.includes(part);

/**
 * 마네킹 부위별 나타난 증상 개수.
 * withLegacy면 예전 부위(팔·다리 등)도 대응하는 마네킹 부위에 칠한다 — 한 부위에 겹치면 많은 쪽.
 */
export function bodyMapSignCounts(skinConditions: SkinCondition[], { withLegacy = false } = {}): BodyMapCounts {
  const counts: BodyMapCounts = {};
  for (const condition of skinConditions) {
    const count = new Set(condition.skinSigns ?? []).size;
    const targets = isBodyMapPart(condition.bodyPart)
      ? [condition.bodyPart]
      : withLegacy
        ? (LEGACY_PART_MAP[condition.bodyPart] ?? [])
        : [];
    for (const part of targets) counts[part] = Math.max(counts[part] ?? 0, count);
  }
  return counts;
}

const SKIN_SIGN_ORDER = Object.keys(SKIN_SIGN_LABELS) as SkinSign[];

/** 증상을 정해진 순서(홍반, 부종/구진, 진물/딱지 …)로 중복 없이 정렬한다 */
export const sortSkinSigns = (signs: Iterable<SkinSign>): SkinSign[] => {
  const set = new Set(signs);
  return SKIN_SIGN_ORDER.filter((sign) => set.has(sign));
};

/** 부위마다 고른 증상을 모두 합친 것 */
export const partSkinSigns = (skinConditions: SkinCondition[] | undefined): SkinSign[] =>
  sortSkinSigns((skinConditions ?? []).flatMap((c) => c.skinSigns ?? []));

/**
 * 그날 나타난 증상 — 부위마다 고른 증상과 부위 없이 고른 증상(예전 기록·챗봇)을 합친다.
 * 예: 목에 홍반, 왼손에 진물/딱지 → [홍반, 진물/딱지]
 */
export const allSkinSigns = (record: { skinConditions?: SkinCondition[]; skinSigns?: SkinSign[] | null }): SkinSign[] =>
  sortSkinSigns([...partSkinSigns(record.skinConditions), ...(record.skinSigns ?? [])]);

/** 부위에 속하지 않는 증상만 남긴다 — 서버의 하루 증상은 부위 증상을 합친 값이라, 수정할 때 부위 증상을 빼야 지운 증상이 남지 않는다 */
export const dailyOnlySkinSigns = (record: { skinConditions?: SkinCondition[]; skinSigns?: SkinSign[] | null }): SkinSign[] => {
  const fromParts = new Set(partSkinSigns(record.skinConditions));
  return sortSkinSigns((record.skinSigns ?? []).filter((sign) => !fromParts.has(sign)));
};

/** 기록하지 않은 부위 색 */
export const BODY_MAP_EMPTY_COLOR = '#e5e7eb';

/** 증상 개수의 최대값 — 증상 7가지가 모두 나타나면 가장 붉다 */
export const SIGN_COUNT_MAX = SKIN_SIGN_ORDER.length;

/**
 * 증상 개수 색: 0개는 하얀색, 많아질수록 붉어져 7개가 모두 나타나면 새빨간색. 기록하지 않은 부위는 회색.
 */
export function signCountColor(count: number | undefined): string {
  if (count === undefined) return BODY_MAP_EMPTY_COLOR;
  const t = Math.min(Math.max(count, 0) / SIGN_COUNT_MAX, 1);
  return `hsl(0 100% ${100 - t * 50}%)`;
}
