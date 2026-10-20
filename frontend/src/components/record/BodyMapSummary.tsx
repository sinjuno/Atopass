import { useState } from 'react';
import type { BodyPart, SkinCondition } from '@/types/record';
import { bodyMapSignCounts } from '@/utils/bodyMap';
import { BODY_PART_LABELS, EMPTY_TEXT, SKIN_SIGN_LABELS } from '@/utils/constants';
import BodyMap, { BodyMapLegend } from './BodyMap';

interface Props {
  skinConditions: SkinCondition[];
}

/**
 * 부위별 증상 마네킹 (읽기 전용). 부위 색은 그 부위에 나타난 증상 개수에 비례하고, 부위를 누르면 증상을 보여 준다.
 * 예전 부위(팔·다리)로 남긴 기록도 해당하는 마네킹 부위에 칠한다.
 */
export default function BodyMapSummary({ skinConditions }: Props) {
  const [selectedPart, setSelectedPart] = useState<BodyPart | null>(null);
  const partCounts = bodyMapSignCounts(skinConditions, { withLegacy: true });
  const selectedCount = selectedPart ? partCounts[selectedPart] : undefined;
  const selectedSigns = skinConditions.find((c) => c.bodyPart === selectedPart)?.skinSigns ?? [];

  return (
    <>
      <BodyMap counts={partCounts} selected={selectedPart} onSelect={setSelectedPart} />
      <div className="mt-3">
        <BodyMapLegend />
      </div>
      <p role="status" className="mt-3 rounded-lg bg-gray-50 py-2.5 text-center text-base">
        {selectedPart ? (
          <>
            <span className="font-semibold text-gray-800">{BODY_PART_LABELS[selectedPart]}</span>{' '}
            {selectedCount === undefined ? (
              <span className="text-gray-400">{EMPTY_TEXT}</span>
            ) : selectedSigns.length > 0 ? (
              <span className="text-gray-700">{selectedSigns.map((sign) => SKIN_SIGN_LABELS[sign]).join(', ')}</span>
            ) : (
              <span className="text-gray-500">나타난 증상 없음</span>
            )}
          </>
        ) : (
          <span className="text-sm text-gray-500">부위를 누르면 나타난 증상을 볼 수 있어요.</span>
        )}
      </p>
    </>
  );
}
