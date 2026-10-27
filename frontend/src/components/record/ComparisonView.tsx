import { useState, type ReactNode } from 'react';
import { Link } from 'react-router';
import type { ComparisonDay, ComparisonResponse } from '@/types/analysis';
import type { BodyPart, SkinSign, SymptomKey } from '@/types/record';
import {
  BODY_PART_LABELS,
  EMPTY_TEXT,
  OUTDOOR_ACTIVITY_LABELS,
  SKIN_SIGN_LABELS,
  SLEEP_SCORE_MAX,
  SYMPTOM_LABELS,
} from '@/utils/constants';
import { formatKoreanDate } from '@/utils/date';
import { bodyPartScoreOf } from '@/utils/recordScore';
import { recordPath } from '@/utils/routes';
import SavedPhotoList from './SavedPhotoList';

interface Props {
  dateA: string;
  dateB: string;
  comparison: ComparisonResponse;
}

const BODY_PART_ORDER = Object.keys(BODY_PART_LABELS) as BodyPart[];
const SYMPTOM_ORDER = Object.keys(SYMPTOM_LABELS) as SymptomKey[];
const SKIN_SIGN_ORDER = Object.keys(SKIN_SIGN_LABELS) as SkinSign[];

/** 그날 해당 부위의 증상 점수(증상 평균). 기록이 없거나 증상을 기록하지 않은 부위는 null */
const scoreOf = (day: ComparisonDay | null, bodyPart: BodyPart) => bodyPartScoreOf(day?.skinConditions, bodyPart);

/** 그날 해당 부위·증상 점수. 기록하지 않은 증상은 null */
function symptomScoreOf(day: ComparisonDay | null, bodyPart: BodyPart, symptom: SymptomKey): number | null {
  const score = day?.skinConditions.find((c) => c.bodyPart === bodyPart)?.symptoms[symptom];
  return typeof score === 'number' ? score : null;
}

const scoreText = (score: number | null) => (score === null ? null : `${score}점`);

/** 두 날 중 한쪽이라도 증상을 기록한 부위 (부위 순서 고정) */
function bodyPartRows(a: ComparisonDay | null, b: ComparisonDay | null) {
  return BODY_PART_ORDER.filter((bodyPart) => scoreOf(a, bodyPart) !== null || scoreOf(b, bodyPart) !== null);
}

/** 두 날짜(A·B) 기록을 나란히 비교한다. 기록이 없는 쪽은 "기록 없음"으로 보여 준다 */
export default function ComparisonView({ dateA, dateB, comparison }: Props) {
  const { A: a, B: b } = comparison;
  const rows = bodyPartRows(a, b);

  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-2">
        <DayHeader tag="A" date={dateA} day={a} />
        <DayHeader tag="B" date={dateB} day={b} />
      </div>

      {!a && !b ? (
        <p className="rounded-2xl bg-gray-50 py-8 text-center text-gray-400">두 날짜 모두 기록이 없어요.</p>
      ) : (
        <>
          <CompareSection title="오늘의 상태">
            <CompareRow
              label="불편함 지수"
              valueA={scoreText(a?.painIndex ?? null)}
              valueB={scoreText(b?.painIndex ?? null)}
              scoreA={a?.painIndex ?? null}
              scoreB={b?.painIndex ?? null}
            />
            <CompareRow label="약 복용" valueA={medicationText(a)} valueB={medicationText(b)} />
          </CompareSection>

          <CompareSection title="나타난 증상" hint="체크된 증상이 그날 나타난 증상이에요.">
            {SKIN_SIGN_ORDER.map((sign) => (
              <SkinSignCompareRow key={sign} sign={sign} a={a} b={b} />
            ))}
          </CompareSection>

          <CompareSection title="부위별 고통 점수" hint="부위마다 기록한 고통 점수예요.">
            {rows.length === 0 ? (
              <p className="py-2 text-center text-gray-400">{EMPTY_TEXT}</p>
            ) : (
              rows.map((bodyPart) => <BodyPartCompareRow key={bodyPart} bodyPart={bodyPart} a={a} b={b} />)
            )}
          </CompareSection>

          <CompareSection title="수면·생활">
            <CompareRow label="수면" valueA={sleepText(a)} valueB={sleepText(b)} />
            <CompareRow
              label={`수면 점수 (${SLEEP_SCORE_MAX}점 만점)`}
              valueA={scoreText(a?.sleepScore ?? null)}
              valueB={scoreText(b?.sleepScore ?? null)}
              scoreA={a?.sleepScore ?? null}
              scoreB={b?.sleepScore ?? null}
              higherIsBetter
            />
            <CompareRow label="음주" valueA={alcoholText(a)} valueB={alcoholText(b)} />
            <CompareRow label="외출" valueA={outdoorText(a)} valueB={outdoorText(b)} />
          </CompareSection>

          <CompareSection title="특이사항">
            <div className="grid grid-cols-2 gap-2">
              <NoteCell day={a} />
              <NoteCell day={b} />
            </div>
          </CompareSection>

          <CompareSection title="사진">
            <div className="grid grid-cols-2 gap-2">
              <PhotoCell day={a} />
              <PhotoCell day={b} />
            </div>
          </CompareSection>
        </>
      )}
    </div>
  );
}

const sleepText = (day: ComparisonDay | null) => (day?.sleepHours == null ? null : `${day.sleepHours}시간`);
const medicationText = (day: ComparisonDay | null) =>
  day?.medication == null ? null : day.medication ? '복용했어요' : '안 했어요';
const alcoholText = (day: ComparisonDay | null) => {
  const alcohol = day?.lifestyle?.alcohol;
  return alcohol == null ? null : alcohol ? '했어요' : '안 했어요';
};
const outdoorText = (day: ComparisonDay | null) => {
  const outdoor = day?.lifestyle?.outdoorActivity;
  return outdoor ? OUTDOOR_ACTIVITY_LABELS[outdoor] : null;
};

function DayHeader({ tag, date, day }: { tag: 'A' | 'B'; date: string; day: ComparisonDay | null }) {
  return (
    <div className="rounded-xl bg-gray-50 p-3 text-center">
      <p className="flex items-center justify-center gap-1.5 text-sm text-gray-500">
        <span className="flex size-5 items-center justify-center rounded-full bg-gray-800 text-xs font-bold text-white">
          {tag}
        </span>
        {day ? '기록 있음' : EMPTY_TEXT}
      </p>
      <p className="mt-1 font-bold">{formatKoreanDate(date)}</p>
      {day && (
        <Link to={recordPath(date)} className="mt-1 inline-block text-sm text-brand-700 underline">
          그날 기록 보기
        </Link>
      )}
    </div>
  );
}

function CompareSection({ title, hint, children }: { title: string; hint?: string; children: ReactNode }) {
  return (
    <section>
      <h3 className="mb-2 font-bold">{title}</h3>
      <div className="flex flex-col divide-y divide-gray-100 rounded-xl border border-gray-200 px-3">
        <div className={`${ROW_GRID} py-2`}>
          <span />
          <DayTag tag="A" />
          <DayTag tag="B" />
        </div>
        {children}
      </div>
      {hint && <p className="mt-1.5 text-sm text-gray-400">{hint}</p>}
    </section>
  );
}

/** 부위 한 줄(증상 평균). 누르면 두 날 중 한쪽이라도 기록한 증상별 A·B 점수를 펼친다 */
function BodyPartCompareRow({ bodyPart, a, b }: { bodyPart: BodyPart; a: ComparisonDay | null; b: ComparisonDay | null }) {
  const [open, setOpen] = useState(false);
  const scoreA = scoreOf(a, bodyPart);
  const scoreB = scoreOf(b, bodyPart);
  const symptoms = SYMPTOM_ORDER.filter(
    (symptom) => symptomScoreOf(a, bodyPart, symptom) !== null || symptomScoreOf(b, bodyPart, symptom) !== null,
  );

  return (
    <div>
      <button type="button" aria-expanded={open} onClick={() => setOpen((v) => !v)} className="w-full text-left">
        <CompareRow
          label={
            <span className="flex items-center gap-1.5">
              {BODY_PART_LABELS[bodyPart]}
              <span aria-hidden className={`text-gray-400 transition-transform ${open ? 'rotate-180' : ''}`}>
                ▾
              </span>
            </span>
          }
          valueA={scoreText(scoreA)}
          valueB={scoreText(scoreB)}
          scoreA={scoreA}
          scoreB={scoreB}
        />
      </button>
      {open && (
        <div className="mb-2.5 flex flex-col divide-y divide-gray-100 rounded-lg bg-gray-50 px-3 text-sm">
          {symptoms.map((symptom) => {
            const symptomA = symptomScoreOf(a, bodyPart, symptom);
            const symptomB = symptomScoreOf(b, bodyPart, symptom);
            return (
              <CompareRow
                key={symptom}
                label={SYMPTOM_LABELS[symptom]}
                valueA={scoreText(symptomA)}
                valueB={scoreText(symptomB)}
                scoreA={symptomA}
                scoreB={symptomB}
              />
            );
          })}
        </div>
      )}
    </div>
  );
}

/** 항목 이름 / A 값 / B 값 칸. 값은 가운데 정렬한다 */
const ROW_GRID = 'grid grid-cols-[1fr_6rem_6rem] items-center gap-2';

function DayTag({ tag }: { tag: 'A' | 'B' }) {
  return (
    <span className="mx-auto flex size-5 items-center justify-center rounded-full bg-gray-800 text-xs font-bold text-white">
      {tag}
    </span>
  );
}

/**
 * 한 줄: 항목 이름 / A 값 / B 값. 둘 다 점수(scoreA·scoreB)이고 다르면 높은 쪽을 색으로 칠하고 ▲를 붙여 어느 날이 더 높은지 바로 보이게 하고,
 * A·B 칸 아래에 차이를 적는다. 고통처럼 높을수록 나쁜 점수는 빨강, 수면 점수처럼 높을수록 좋은 점수(higherIsBetter)는 초록이다.
 */
function CompareRow({
  label,
  valueA,
  valueB,
  scoreA = null,
  scoreB = null,
  higherIsBetter = false,
}: {
  label: ReactNode;
  valueA: string | null;
  valueB: string | null;
  scoreA?: number | null;
  scoreB?: number | null;
  higherIsBetter?: boolean;
}) {
  const compared = scoreA !== null && scoreB !== null;
  // 소수 평균끼리 뺄 때 생기는 오차(0.30000000000000004 등)를 없앤다
  const gap = compared ? Math.round(Math.abs(scoreB - scoreA) * 10) / 10 : 0;
  const higher = !compared || gap === 0 ? null : scoreA > scoreB ? 'A' : 'B';
  const tone = higherIsBetter ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-600';

  return (
    <div className="py-2.5">
      <div className={ROW_GRID}>
        <span className="text-gray-700">{label}</span>
        <Value text={valueA} highlight={higher === 'A' ? tone : null} />
        <Value text={valueB} highlight={higher === 'B' ? tone : null} />
      </div>
      {higher && (
        <p className={`${ROW_GRID} mt-0.5 text-xs text-gray-500`}>
          <span />
          <span className="col-span-2 text-center">
            {higher}가 {gap}점 더 높아요
          </span>
        </p>
      )}
    </div>
  );
}

function Value({ text, highlight = null }: { text: string | null; highlight?: string | null }) {
  if (!text) return <span className="block text-center text-sm text-gray-400">{EMPTY_TEXT}</span>;
  return (
    <span
      className={`mx-auto block w-fit rounded-full px-2.5 py-0.5 text-center ${
        highlight ? `${highlight} font-bold` : 'font-semibold text-gray-900'
      }`}
    >
      {highlight && <span aria-hidden="true">▲ </span>}
      {text}
    </span>
  );
}

/** 나타난 증상 한 줄: 증상 이름 / A 체크 / B 체크. 두 날이 다르면 이름을 굵게, 기록이 없는 날은 "-" */
function SkinSignCompareRow({ sign, a, b }: { sign: SkinSign; a: ComparisonDay | null; b: ComparisonDay | null }) {
  const hasA = a ? a.skinSigns.includes(sign) : null;
  const hasB = b ? b.skinSigns.includes(sign) : null;
  const differs = hasA !== null && hasB !== null && hasA !== hasB;
  return (
    <div className={`${ROW_GRID} py-2.5`}>
      <span className={differs ? 'font-semibold text-gray-900' : 'text-gray-700'}>{SKIN_SIGN_LABELS[sign]}</span>
      <CheckMark checked={hasA} />
      <CheckMark checked={hasB} />
    </div>
  );
}

function CheckMark({ checked }: { checked: boolean | null }) {
  if (checked === null) return <span className="block text-center text-gray-300">-</span>;
  return (
    <span
      role="img"
      aria-label={checked ? '있음' : '없음'}
      className={`mx-auto flex size-6 items-center justify-center rounded-md border-2 text-sm font-bold ${
        checked ? 'border-brand-600 bg-brand-600 text-white' : 'border-gray-300 bg-white'
      }`}
    >
      {checked && '✓'}
    </span>
  );
}

function NoteCell({ day }: { day: ComparisonDay | null }) {
  return day?.notes ? (
    <p className="whitespace-pre-wrap py-2.5 text-center text-sm text-gray-800">{day.notes}</p>
  ) : (
    <p className="py-2.5 text-center text-sm text-gray-400">{EMPTY_TEXT}</p>
  );
}

function PhotoCell({ day }: { day: ComparisonDay | null }) {
  if (!day) return <p className="py-2.5 text-center text-sm text-gray-400">{EMPTY_TEXT}</p>;
  if (day.photos.length === 0) return <p className="py-2.5 text-center text-sm text-gray-400">사진 없음</p>;
  return (
    <div className="min-w-0 py-2.5">
      <SavedPhotoList recordId={day.recordId} photos={day.photos} />
    </div>
  );
}
