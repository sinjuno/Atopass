import type { ExtractedMedication } from '@/types/medication';
import { REQUIRED_MESSAGE, TimeButtons, TypeButtons } from './MedicationFields';

interface Props {
  index: number;
  checked: boolean;
  value: ExtractedMedication;
  /** 저장을 눌렀는데 이름·종류가 비어 있을 때 true */
  showErrors: boolean;
  onCheckedChange: (checked: boolean) => void;
  onChange: (value: ExtractedMedication) => void;
}

const fieldClass = (empty: boolean, invalid = false) =>
  `w-full rounded-xl border px-3 py-2.5 text-base outline-none focus:ring-2 focus:ring-brand-100 ${
    invalid
      ? 'border-red-300 bg-red-50'
      : empty
        ? 'border-amber-300 bg-amber-50'
        : 'border-gray-200 bg-white'
  }`;

/** 사진 확인 화면의 약 한 개. 바로 고칠 수 있고, 비어 있는 칸은 강조해 보여준다. */
export default function MedicationReviewCard({ index, checked, value, showErrors, onCheckedChange, onChange }: Props) {
  const set = <K extends keyof ExtractedMedication>(key: K, v: ExtractedMedication[K]) =>
    onChange({ ...value, [key]: v });

  const nameEmpty = !value.name?.trim();
  const typeEmpty = !value.type;
  const doseEmpty = !value.dose?.trim();
  const timesEmpty = value.times.length === 0;

  return (
    <li className={`rounded-3xl bg-white p-4 shadow-card ${checked ? '' : 'opacity-50'}`}>
      <label className="mb-3 flex items-center gap-3 font-bold text-gray-700">
        <input
          type="checkbox"
          checked={checked}
          onChange={(e) => onCheckedChange(e.target.checked)}
          className="size-5 accent-brand-600"
        />
        약 {index + 1} 저장하기
      </label>

      <div className="flex flex-col gap-3">
        <div>
          <label className="mb-1 block text-sm font-medium text-gray-600">약 이름</label>
          <input
            value={value.name ?? ''}
            onChange={(e) => set('name', e.target.value)}
            placeholder="읽지 못했어요. 직접 입력해 주세요"
            className={fieldClass(nameEmpty, showErrors && checked && nameEmpty)}
          />
          {showErrors && checked && nameEmpty && <p className="mt-1 text-sm text-red-600">{REQUIRED_MESSAGE}</p>}
        </div>

        <div className={typeEmpty ? 'rounded-xl bg-amber-50 p-2' : ''}>
          <p className="mb-1 text-sm font-medium text-gray-600">종류</p>
          <TypeButtons value={value.type} onChange={(t) => set('type', t)} />
          {showErrors && checked && typeEmpty && <p className="mt-1 text-sm text-red-600">{REQUIRED_MESSAGE}</p>}
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium text-gray-600">1회 용량</label>
          <input
            value={value.dose ?? ''}
            onChange={(e) => set('dose', e.target.value || null)}
            placeholder="비어 있어요"
            className={fieldClass(doseEmpty)}
          />
        </div>

        <div className={timesEmpty ? 'rounded-xl bg-amber-50 p-2' : ''}>
          <p className="mb-1 text-sm font-medium text-gray-600">복용 시간</p>
          {timesEmpty && value.timesText && (
            <p className="mb-2 text-sm text-gray-700">안내문 원문: “{value.timesText}” — 시간대를 골라 주세요.</p>
          )}
          <TimeButtons value={value.times} onChange={(t) => set('times', t)} />
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium text-gray-600">시작일</label>
          <input
            type="date"
            value={value.startDate}
            onChange={(e) => set('startDate', e.target.value)}
            className={fieldClass(!value.startDate)}
          />
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium text-gray-600">메모</label>
          <input
            value={value.memo ?? ''}
            onChange={(e) => set('memo', e.target.value || null)}
            className="w-full rounded-xl border border-gray-200 bg-white px-3 py-2.5 text-base outline-none focus:ring-2 focus:ring-brand-100"
          />
        </div>

        <label className="flex items-center gap-3 text-base text-gray-700">
          <input
            type="checkbox"
            checked={value.isOtherCondition}
            onChange={(e) => set('isOtherCondition', e.target.checked)}
            className="size-5 accent-brand-600"
          />
          다른 지병 때문에 먹는 약이에요
        </label>
      </div>
    </li>
  );
}
