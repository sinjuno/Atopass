import { useState } from 'react';
import type { Medication } from '@/types/medication';
import { today } from '@/utils/date';
import { timeLabel, typeLabel } from '@/utils/medication';

interface Props {
  medication: Medication;
  onEdit?: () => void;
  /** 복용 중단 확정 (endDate 포함). 복용 중인 약에만 넘긴다. */
  onStop?: (endDate: string) => Promise<void>;
}

export function TimeChips({ medication }: { medication: Medication }) {
  if (medication.times.length === 0) {
    return medication.timesText ? <p className="text-sm text-gray-500">{medication.timesText}</p> : null;
  }
  return (
    <ul className="flex flex-wrap gap-1.5">
      {medication.times.map((t) => (
        <li key={t} className="rounded-full bg-brand-50 px-2.5 py-1 text-sm text-brand-700">
          {timeLabel(t)}
        </li>
      ))}
    </ul>
  );
}

/** 약 한 개 카드. 복용 중이면 [수정] [복용 중단], 중단한 약이면 중단일만 보여준다. */
export default function MedicationCard({ medication, onEdit, onStop }: Props) {
  const [confirming, setConfirming] = useState(false);
  const [endDate, setEndDate] = useState(today());
  const [stopping, setStopping] = useState(false);

  const handleStop = async () => {
    if (!onStop || stopping) return;
    setStopping(true);
    try {
      await onStop(endDate);
    } finally {
      setStopping(false);
      setConfirming(false);
    }
  };

  return (
    <li className="flex flex-col gap-2 rounded-3xl bg-white p-4 shadow-card">
      <div className="flex items-baseline justify-between gap-2">
        <p className="text-lg font-bold">{medication.name}</p>
        <span className="shrink-0 rounded-full bg-gray-100 px-2.5 py-1 text-sm text-gray-600">
          {typeLabel(medication.type)}
        </span>
      </div>
      {medication.dose && <p className="text-gray-700">1회 {medication.dose}</p>}
      <TimeChips medication={medication} />
      {medication.endDate && (
        <p className="text-sm text-gray-500">
          {medication.startDate} ~ {medication.endDate}
        </p>
      )}
      {medication.memo && <p className="text-sm text-gray-500">{medication.memo}</p>}

      {(onEdit || onStop) &&
        (confirming ? (
          <div className="mt-1 flex flex-col gap-2 rounded-xl bg-gray-50 p-3">
            <label className="text-sm text-gray-700">
              복용 중단일
              <input
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="mt-1 block w-full rounded-xl border border-gray-200 bg-white px-3 py-2 text-base"
              />
            </label>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setConfirming(false)}
                className="flex-1 rounded-xl border border-gray-200 bg-white py-2.5 font-bold text-gray-600"
              >
                취소
              </button>
              <button
                type="button"
                onClick={() => void handleStop()}
                disabled={!endDate || stopping}
                className="flex-1 rounded-xl bg-red-500 py-2.5 font-bold text-white disabled:opacity-40"
              >
                {stopping ? '처리 중…' : '복용 중단'}
              </button>
            </div>
          </div>
        ) : (
          <div className="mt-1 flex gap-2">
            {onEdit && (
              <button
                type="button"
                onClick={onEdit}
                className="flex-1 rounded-xl border border-gray-200 bg-white py-2.5 font-bold text-gray-700"
              >
                수정
              </button>
            )}
            {onStop && (
              <button
                type="button"
                onClick={() => setConfirming(true)}
                className="flex-1 rounded-xl border border-red-200 bg-white py-2.5 font-bold text-red-500"
              >
                복용 중단
              </button>
            )}
          </div>
        ))}
    </li>
  );
}
