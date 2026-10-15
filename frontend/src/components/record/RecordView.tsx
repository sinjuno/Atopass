import type { PhotoRef, RecordInput, SkinCondition, SymptomKey } from '@/types/record';
import {
  BODY_PART_LABELS,
  EMPTY_TEXT,
  OUTDOOR_ACTIVITY_LABELS,
  PAIN_INDEX_MAX,
  SCORE_MAX,
  SKIN_SIGN_LABELS,
  SLEEP_SCORE_MAX,
  SYMPTOM_LABELS,
} from '@/utils/constants';
import { representativeScore } from '@/utils/recordScore';
import { sleepScore } from '@/utils/sleepScore';
import SavedPhotoList from './SavedPhotoList';
import Section from './Section';

interface Props {
  record: RecordInput;
  recordId: number;
  photos: PhotoRef[];
}

/** 점수 구간별 색 (0~3 / 4~6 / 7~10) */
function scoreTone(score: number) {
  if (score <= 3) return { bar: 'bg-emerald-500', badge: 'bg-emerald-50 text-emerald-700' };
  if (score <= 6) return { bar: 'bg-amber-400', badge: 'bg-amber-50 text-amber-700' };
  return { bar: 'bg-red-500', badge: 'bg-red-50 text-red-700' };
}

/** 저장된 하루 기록 보기 (읽기 전용) */
export default function RecordView({ record, recordId, photos }: Props) {
  const topScore = representativeScore(record.skinConditions);
  const alcohol = record.lifestyle?.alcohol;
  const outdoor = record.lifestyle?.outdoorActivity;
  const medication = record.medication;
  const skinSigns = record.skinSigns ?? [];
  const sleep = sleepScore(record.sleepHours);

  return (
    <>
      <div className="px-4 pt-4">
        <div className="flex items-center justify-between rounded-2xl bg-gray-50 p-4">
          <p className="text-gray-600">가장 높은 증상 점수</p>
          {topScore === null ? (
            <p className="text-gray-400">{EMPTY_TEXT}</p>
          ) : (
            <p className={`rounded-full px-3 py-1 text-lg font-bold ${scoreTone(topScore).badge}`}>{topScore}점</p>
          )}
        </div>
      </div>

      <Section title="오늘의 상태">
        <dl className="grid grid-cols-2 gap-2 text-center">
          <InfoTile label="고통 지수" value={record.painIndex == null ? null : `${record.painIndex}/${PAIN_INDEX_MAX}점`} />
          <InfoTile label="약 복용" value={medication == null ? null : medication ? '예' : '아니오'} />
        </dl>
        <p className="mt-3 text-sm text-gray-500">나타난 증상</p>
        {skinSigns.length === 0 ? (
          <p className="text-gray-400">{record.skinSigns == null ? EMPTY_TEXT : '없음'}</p>
        ) : (
          <p className="text-base text-gray-800">{skinSigns.map((sign) => SKIN_SIGN_LABELS[sign]).join(', ')}</p>
        )}
      </Section>

      <Section title="부위·증상">
        {record.skinConditions.length === 0 ? (
          <p className="text-gray-400">{EMPTY_TEXT}</p>
        ) : (
          <div className="flex flex-col gap-3">
            {record.skinConditions.map((condition) => (
              <BodyPartCard key={condition.bodyPart} condition={condition} />
            ))}
          </div>
        )}
      </Section>

      <Section title="수면·생활">
        <dl className="grid grid-cols-3 gap-2 text-center">
          <InfoTile
            label="수면"
            value={record.sleepHours == null ? null : `${record.sleepHours}시간 (${sleep}/${SLEEP_SCORE_MAX}점)`}
          />
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

/** 부위 하나: 기록한 증상만 점수 높은 순으로 막대와 함께 보여 준다 */
function BodyPartCard({ condition }: { condition: SkinCondition }) {
  const symptoms = (Object.entries(condition.symptoms) as [SymptomKey, number | undefined][])
    .filter((entry): entry is [SymptomKey, number] => typeof entry[1] === 'number')
    .sort((a, b) => b[1] - a[1]);

  return (
    <div className="rounded-xl border border-gray-200 p-4">
      <h3 className="mb-3 font-bold">{BODY_PART_LABELS[condition.bodyPart]}</h3>
      {symptoms.length === 0 ? (
        <p className="text-gray-400">기록한 증상 없음</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {symptoms.map(([key, score]) => (
            <li key={key} className="grid grid-cols-[4.5rem_1fr_2.5rem] items-center gap-3">
              <span className="text-gray-700">{SYMPTOM_LABELS[key]}</span>
              <span className="h-2.5 overflow-hidden rounded-full bg-gray-100">
                <span
                  className={`block h-full rounded-full ${scoreTone(score).bar}`}
                  style={{ width: `${(score / SCORE_MAX) * 100}%` }}
                />
              </span>
              <span className="text-right font-semibold">{score}점</span>
            </li>
          ))}
        </ul>
      )}
    </div>
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
