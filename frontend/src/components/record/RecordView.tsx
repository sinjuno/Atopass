import type { PhotoRef, RecordInput, SituationalAnswerItem } from '@/types/record';
import { allSkinSigns } from '@/utils/bodyMap';
import {
  EMPTY_TEXT,
  OUTDOOR_ACTIVITY_LABELS,
  PAIN_INDEX_MAX,
  SKIN_SIGN_LABELS,
} from '@/utils/constants';
import BodyMapSummary from './BodyMapSummary';
import SavedPhotoList from './SavedPhotoList';
import Section from './Section';

interface Props {
  record: RecordInput;
  recordId: number;
  photos: PhotoRef[];
  /** 그날 챗봇이 물어 받은 답 (상황 질문·마무리 질문). 없으면 구역을 보여 주지 않는다 */
  situationalAnswers?: SituationalAnswerItem[];
}

/** 고통 지수 구간별 색 (0~3 / 4~6 / 7~10) */
function painTone(score: number) {
  if (score <= 3) return 'bg-emerald-50 text-emerald-700';
  if (score <= 6) return 'bg-amber-50 text-amber-700';
  return 'bg-red-50 text-red-700';
}

/** 저장된 하루 기록 보기 (읽기 전용) */
export default function RecordView({ record, recordId, photos, situationalAnswers = [] }: Props) {
  // 부위마다 고른 증상을 합친 그날의 증상 (예: 목 홍반, 왼손 진물/딱지 → 홍반, 진물/딱지)
  const skinSigns = allSkinSigns(record);
  const painIndex = record.painIndex ?? null;
  const alcohol = record.lifestyle?.alcohol;
  const outdoor = record.lifestyle?.outdoorActivity;

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
          <BodyMapSummary skinConditions={record.skinConditions} />
        </div>
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

      {situationalAnswers.length > 0 && (
        <Section title="챗봇에 남긴 이야기">
          <dl className="flex flex-col gap-3">
            {situationalAnswers.map((item, i) => (
              <div key={`${item.questionId}-${i}`}>
                <dt className="text-sm text-gray-500">{item.title}</dt>
                <dd className="mt-0.5 whitespace-pre-wrap text-base text-gray-800">{item.answer}</dd>
              </div>
            ))}
          </dl>
        </Section>
      )}

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
