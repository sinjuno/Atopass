import { useState } from 'react';
import type { BodyPart, PhotoRef, RecordInput } from '@/types/record';
import { allSkinSigns, bodyMapSignCounts } from '@/utils/bodyMap';
import {
  BODY_PART_LABELS,
  EMPTY_TEXT,
  LINKED_ANSWER_LABELS,
  MEDICATION_USE_LABELS,
  OUTDOOR_ACTIVITY_LABELS,
  PAIN_INDEX_MAX,
  SKIN_SIGN_LABELS,
  SKIN_TREND_LABELS,
} from '@/utils/constants';
import BodyMap, { BodyMapLegend } from './BodyMap';
import SavedPhotoList from './SavedPhotoList';
import Section from './Section';

interface Props {
  record: RecordInput;
  recordId: number;
  photos: PhotoRef[];
}

/** 고통 지수 구간별 색 (0~3 / 4~6 / 7~10) */
function painTone(score: number) {
  if (score <= 3) return 'bg-emerald-50 text-emerald-700';
  if (score <= 6) return 'bg-amber-50 text-amber-700';
  return 'bg-red-50 text-red-700';
}

/** 저장된 하루 기록 보기 (읽기 전용) */
export default function RecordView({ record, recordId, photos }: Props) {
  const [selectedPart, setSelectedPart] = useState<BodyPart | null>(null);
  // 예전 부위(팔·다리)로 남긴 기록도 해당하는 마네킹 부위에 칠한다. 색은 나타난 증상 개수에 비례한다
  const partCounts = bodyMapSignCounts(record.skinConditions, { withLegacy: true });
  const selectedCount = selectedPart ? partCounts[selectedPart] : undefined;
  const selectedSigns = record.skinConditions.find((c) => c.bodyPart === selectedPart)?.skinSigns ?? [];
  // 부위마다 고른 증상을 합친 그날의 증상 (예: 목 홍반, 왼손 진물/딱지 → 홍반, 진물/딱지)
  const skinSigns = allSkinSigns(record);
  const painIndex = record.painIndex ?? null;
  const alcohol = record.lifestyle?.alcohol;
  const outdoor = record.lifestyle?.outdoorActivity;
  const check = record.dailyCheck;
  const linkedAnswers = Object.entries(check?.linkedAnswers ?? {}).sort(([a], [b]) => a.localeCompare(b));

  return (
    <>
      <div className="px-4 pt-4">
        <div className="flex items-center justify-between rounded-2xl bg-gray-50 p-4">
          <p className="text-gray-600">고통 지수</p>
          {painIndex === null ? (
            <p className="text-gray-400">{EMPTY_TEXT}</p>
          ) : (
            <p className={`rounded-full px-3 py-1 text-lg font-bold ${painTone(painIndex)}`}>
              {painIndex}점 <span className="text-sm font-normal">/ {PAIN_INDEX_MAX}</span>
            </p>
          )}
        </div>
        <div className="mt-2 rounded-2xl bg-gray-50 p-4">
          <p className="text-gray-600">나타난 증상</p>
          {skinSigns.length === 0 ? (
            <p className="mt-1 text-gray-400">{EMPTY_TEXT}</p>
          ) : (
            <p className="mt-1 text-lg font-semibold text-gray-900">
              {skinSigns.map((sign) => SKIN_SIGN_LABELS[sign]).join(', ')}
            </p>
          )}
        </div>
      </div>

      <Section title="고통 부위·증상">
        <div className="rounded-xl border border-gray-200 p-4">
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
        </div>
      </Section>

      <Section title="오늘 점검">
        <dl className="grid grid-cols-3 gap-2 text-center">
          <InfoTile label="피부 상태" value={check?.skinTrend ? SKIN_TREND_LABELS[check.skinTrend] : null} />
          <InfoTile label="가려움" value={check?.itchScore == null ? null : `${check.itchScore}점`} />
          <InfoTile
            label="잠 설침"
            value={check?.sleepDisturbed == null ? null : check.sleepDisturbed ? '설쳤어요' : '괜찮았어요'}
          />
          <InfoTile label="보습제" value={check?.moisturizer == null ? null : check.moisturizer ? '발랐어요' : '안 발랐어요'} />
          <InfoTile label="연고·약" value={check?.medicationUse ? MEDICATION_USE_LABELS[check.medicationUse] : null} />
        </dl>
        {linkedAnswers.length > 0 && (
          <dl className="mt-3 flex flex-col gap-1.5 rounded-xl bg-gray-50 p-3 text-base">
            {linkedAnswers.map(([id, answer]) => (
              <div key={id} className="flex gap-2">
                <dt className="shrink-0 text-gray-500">{LINKED_ANSWER_LABELS[id] ?? id}</dt>
                <dd className="text-gray-800">{answer}</dd>
              </div>
            ))}
          </dl>
        )}
      </Section>

      <Section title="수면·생활">
        <dl className="grid grid-cols-3 gap-2 text-center">
          <InfoTile label="수면" value={record.sleepHours == null ? null : `${record.sleepHours}시간`} />
          <InfoTile label="음주" value={alcohol == null ? null : alcohol ? '했어요' : '안 했어요'} />
          <InfoTile label="외출" value={outdoor ? OUTDOOR_ACTIVITY_LABELS[outdoor] : null} />
        </dl>
      </Section>

      <Section title="특이사항">
        {record.notes ? (
          <p className="whitespace-pre-wrap text-base text-gray-800">{record.notes}</p>
        ) : (
          <p className="text-gray-400">{EMPTY_TEXT}</p>
        )}
      </Section>

      <Section title="사진">
        {photos.length === 0 ? (
          <p className="text-gray-400">사진 없음</p>
        ) : (
          <SavedPhotoList recordId={recordId} photos={photos} />
        )}
      </Section>
    </>
  );
}

function InfoTile({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="rounded-xl bg-gray-50 py-3">
      <dt className="text-sm text-gray-500">{label}</dt>
      <dd className={`mt-1 text-lg font-bold ${value ? '' : 'text-base font-normal text-gray-400'}`}>
        {value ?? EMPTY_TEXT}
      </dd>
    </div>
  );
}
