import { useState } from 'react';
import { Navigate, useNavigate, useParams } from 'react-router';
import { ApiError } from '@/apis/client';
import PageHeader from '@/components/common/PageHeader';
import LifestyleEditor from '@/components/record/LifestyleEditor';
import PendingPhotoList from '@/components/record/PendingPhotoList';
import SavedPhotoList from '@/components/record/SavedPhotoList';
import Section from '@/components/record/Section';
import SkinConditionEditor from '@/components/record/SkinConditionEditor';
import { useDailyRecord, type DailyRecordMode } from '@/hooks/useDailyRecord';
import { useRecordDraft } from '@/hooks/useRecordDraft';
import { PHOTO_ACCEPT } from '@/utils/constants';
import { formatKoreanDate, isValidDateString } from '@/utils/date';
import { validatePhotos } from '@/utils/photo';

const MODE_LABEL: Record<DailyRecordMode, string> = {
  edit: '저장된 기록',
  chatDraft: '챗봇이 정리한 내용을 확인해 주세요',
  new: '아직 기록이 없어요. 직접 입력할 수 있어요',
};

/** 일일 총정리 — /record/:date. 하루 기록을 한 화면에서 확인·수정·저장 */
export default function RecordSummaryPage() {
  const { date } = useParams();
  if (!isValidDateString(date)) return <Navigate to="/" replace />;
  return <RecordSummary key={date} date={date} />;
}

function RecordSummary({ date }: { date: string }) {
  const navigate = useNavigate();
  const { mode, recordId, form, setForm, photos, loading, error, save, remove, removePhoto } = useDailyRecord(date);
  const draft = useRecordDraft(date);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [confirmDelete, setConfirmDelete] = useState(false);

  if (loading) return <p className="p-6 text-center text-gray-500">기록을 불러오는 중…</p>;
  if (error) return <p className="p-6 text-center text-red-500">{error}</p>;

  const handleSave = async () => {
    setMessage(null);
    setFieldErrors({});
    // RecordRequest.skinConditions는 minItems: 1
    if (form.skinConditions.length === 0) {
      setFieldErrors({ skinConditions: '증상이 있었던 부위를 하나 이상 추가해 주세요.' });
      return;
    }
    setSaving(true);
    try {
      const failed = await save(draft.photos);
      draft.clearPhotos();
      if (failed.length) {
        draft.addPhotos(failed);
        setMessage(`기록은 저장했지만 사진 ${failed.length}장을 올리지 못했어요. 다시 저장해 주세요.`);
      } else {
        setMessage('기록이 저장되었어요.');
      }
    } catch (e) {
      if (e instanceof ApiError) {
        // 검증 오류는 error.details의 field를 해당 입력칸에 표시
        setFieldErrors(Object.fromEntries(e.details.map((d) => [d.field, d.reason])));
        setMessage(e.message);
      } else {
        setMessage('저장하지 못했어요. 잠시 후 다시 시도해 주세요.');
      }
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    try {
      await remove();
      navigate('/', { replace: true });
    } catch (e) {
      setMessage(e instanceof Error ? e.message : '삭제하지 못했어요.');
    } finally {
      setConfirmDelete(false);
    }
  };

  const handleAddPhotos = (files: File[]) => {
    const err = validatePhotos(files, photos.length + draft.photos.length);
    if (err) setMessage(err);
    else draft.addPhotos(files);
  };

  return (
    <div>
      <PageHeader title={formatKoreanDate(date)} />
      <p className="bg-gray-50 px-4 py-2 text-sm text-gray-600">{MODE_LABEL[mode]}</p>

      <Section title="부위·증상">
        <SkinConditionEditor
          value={form.skinConditions}
          onChange={(skinConditions) => setForm({ ...form, skinConditions })}
        />
        <FieldError errors={fieldErrors} prefix="skinConditions" />
      </Section>

      <Section title="수면·생활">
        <LifestyleEditor
          sleepHours={form.sleepHours}
          lifestyle={form.lifestyle}
          onChange={(next) => setForm({ ...form, ...next })}
        />
        <FieldError errors={fieldErrors} prefix="sleepHours" />
        <FieldError errors={fieldErrors} prefix="lifestyle" />
      </Section>

      <Section title="특이사항">
        <textarea
          value={form.notes ?? ''}
          onChange={(e) => setForm({ ...form, notes: e.target.value || null })}
          rows={3}
          placeholder="기록 없음"
          className="w-full rounded-xl border border-gray-300 p-3 text-base"
        />
      </Section>

      <Section title="사진">
        <div className="flex flex-col gap-3">
          {recordId && (
            <SavedPhotoList
              recordId={recordId}
              photos={photos}
              onRemove={(id) => removePhoto(id).catch(() => setMessage('사진을 삭제하지 못했어요.'))}
            />
          )}
          <PendingPhotoList files={draft.photos} onRemove={draft.removePhoto} />
          <label className="cursor-pointer rounded-xl border border-dashed border-gray-300 py-3 text-center text-base text-gray-600">
            + 사진 추가
            <input
              type="file"
              accept={PHOTO_ACCEPT.join(',')}
              multiple
              hidden
              onChange={(e) => {
                handleAddPhotos(Array.from(e.target.files ?? []));
                e.target.value = '';
              }}
            />
          </label>
        </div>
      </Section>

      <div className="flex flex-col gap-2 p-4">
        {message && (
          <p role="status" className="text-center text-base text-gray-700">
            {message}
          </p>
        )}
        <button
          type="button"
          onClick={() => void handleSave()}
          disabled={saving}
          className="rounded-xl bg-emerald-600 py-4 text-lg font-bold text-white disabled:bg-gray-300"
        >
          {saving ? '저장 중…' : mode === 'edit' ? '수정 내용 저장' : '확인하고 저장'}
        </button>

        {mode === 'edit' &&
          (confirmDelete ? (
            <div className="flex gap-2">
              <button type="button" onClick={() => setConfirmDelete(false)} className="flex-1 rounded-xl border py-3">
                취소
              </button>
              <button
                type="button"
                onClick={() => void handleDelete()}
                className="flex-1 rounded-xl bg-red-500 py-3 font-semibold text-white"
              >
                사진까지 삭제
              </button>
            </div>
          ) : (
            <button type="button" onClick={() => setConfirmDelete(true)} className="py-3 text-base text-red-500">
              기록 삭제
            </button>
          ))}
      </div>
    </div>
  );
}

/** 서버 검증 오류(error.details) 표시 — field가 "skinConditions[0].symptoms.itching"처럼 와서 앞부분으로 찾는다 */
function FieldError({ errors, prefix }: { errors: Record<string, string>; prefix: string }) {
  const reason = Object.entries(errors).find(([field]) => field.startsWith(prefix))?.[1];
  return reason ? <p className="mt-2 text-sm text-red-500">{reason}</p> : null;
}
