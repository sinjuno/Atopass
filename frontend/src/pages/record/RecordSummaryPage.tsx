import { useState } from 'react';
import { Navigate, useNavigate, useParams } from 'react-router';
import { ApiError } from '@/apis/client';
import PageHeader from '@/components/common/PageHeader';
import DailyConditionEditor from '@/components/record/DailyConditionEditor';
import LifestyleEditor from '@/components/record/LifestyleEditor';
import PainIndexSlider from '@/components/record/PainIndexSlider';
import PendingPhotoList from '@/components/record/PendingPhotoList';
import RecordView from '@/components/record/RecordView';
import SavedPhotoList from '@/components/record/SavedPhotoList';
import Section from '@/components/record/Section';
import SkinConditionEditor from '@/components/record/SkinConditionEditor';
import { useDailyRecord, type DailyRecordMode } from '@/hooks/useDailyRecord';
import { useRecordDraft } from '@/hooks/useRecordDraft';
import type { RecordInput } from '@/types/record';
import { CHAT_NOTES_MAX_LENGTH, NOTES_MAX_LENGTH, PART_PAIN_SYMPTOM, PHOTO_ACCEPT } from '@/utils/constants';
import { formatKoreanDate, isValidDateString } from '@/utils/date';
import { validatePhotos } from '@/utils/photo';

const MODE_LABEL: Record<DailyRecordMode, string> = {
  edit: '기록을 수정하고 있어요',
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
  // 저장된 기록은 보기 모드로 열고, "수정"을 눌렀을 때만 입력할 수 있다. 취소하면 수정 전 값으로 되돌린다.
  const [editSnapshot, setEditSnapshot] = useState<RecordInput | null>(null);

  if (loading) return <p className="p-6 text-center text-gray-500">기록을 불러오는 중…</p>;
  if (error) return <p className="p-6 text-center text-red-500">{error}</p>;

  // 챗봇으로 기록할 때 특이사항은 채팅 한 번(500자)으로 받는다
  const notesMaxLength = mode === 'chatDraft' ? CHAT_NOTES_MAX_LENGTH : NOTES_MAX_LENGTH;

  const isEditing = mode !== 'edit' || editSnapshot !== null;

  const startEdit = () => {
    setMessage(null);
    setEditSnapshot(form);
  };

  const cancelEdit = () => {
    if (editSnapshot) setForm(editSnapshot);
    draft.clearPhotos();
    setFieldErrors({});
    setMessage(null);
    setConfirmDelete(false);
    setEditSnapshot(null);
  };

  if (mode === 'edit' && recordId && !isEditing) {
    return (
      <div>
        <PageHeader
          title={formatKoreanDate(date)}
          right={
            <button
              type="button"
              onClick={startEdit}
              className="rounded-lg border border-gray-300 px-4 py-1.5 text-base font-semibold text-gray-700"
            >
              수정
            </button>
          }
        />
        {message && (
          <p role="status" className="bg-emerald-50 px-4 py-2 text-sm text-emerald-700">
            {message}
          </p>
        )}
        <RecordView record={form} recordId={recordId} photos={photos} />
      </div>
    );
  }

  const handleSave = async () => {
    setMessage(null);
    setFieldErrors({});
    // RecordRequest: 부위별 증상과 고통 지수 중 하나 이상 필요
    if (form.skinConditions.length === 0 && form.painIndex == null) {
      setFieldErrors({ painIndex: '고통 지수를 골라 주세요.' });
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
        setEditSnapshot(null); // 저장한 기록은 보기 모드로 돌아간다
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

  // 고통 지수와 부위는 한 묶음이라, 고통 지수를 바꾸면 기록한 부위의 고통 점수도 같이 바뀐다
  const setPainIndex = (painIndex: number) =>
    setForm({
      ...form,
      painIndex,
      skinConditions: form.skinConditions.map((c) => ({ ...c, symptoms: { [PART_PAIN_SYMPTOM]: painIndex } })),
    });

  const handleAddPhotos = (files: File[]) => {
    const err = validatePhotos(files, photos.length + draft.photos.length);
    if (err) setMessage(err);
    else draft.addPhotos(files);
  };

  return (
    <div>
      <PageHeader
        title={formatKoreanDate(date)}
        right={
          mode === 'edit' && (
            <button type="button" onClick={cancelEdit} className="rounded-lg px-3 py-1.5 text-base text-gray-500">
              취소
            </button>
          )
        }
      />
      <p className="bg-gray-50 px-4 py-2 text-sm text-gray-600">{MODE_LABEL[mode]}</p>

      <Section title="고통 지수·고통 부위">
        <div className="flex flex-col gap-4">
          <PainIndexSlider value={form.painIndex ?? null} onChange={setPainIndex} />
          <SkinConditionEditor
            value={form.skinConditions}
            painIndex={form.painIndex ?? null}
            onChange={(skinConditions) => setForm({ ...form, skinConditions })}
          />
        </div>
        <FieldError errors={fieldErrors} prefix="painIndex" />
        <FieldError errors={fieldErrors} prefix="skinConditions" />
      </Section>

      <Section title="오늘의 상태">
        <DailyConditionEditor
          medication={form.medication}
          skinSigns={form.skinSigns}
          skinConditions={form.skinConditions}
          onChange={(next) => setForm({ ...form, ...next })}
        />
        <FieldError errors={fieldErrors} prefix="medication" />
        <FieldError errors={fieldErrors} prefix="skinSigns" />
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
          maxLength={notesMaxLength}
          placeholder="기록 없음"
          className="w-full rounded-xl border border-gray-300 p-3 text-base"
        />
        <p className="mt-1 text-right text-sm text-gray-400">
          {(form.notes ?? '').length}/{notesMaxLength}
        </p>
        <FieldError errors={fieldErrors} prefix="notes" />
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

/** 서버 검증 오류(error.details) 표시 — field가 "skinConditions[0].symptoms.pain"처럼 와서 앞부분으로 찾는다 */
function FieldError({ errors, prefix }: { errors: Record<string, string>; prefix: string }) {
  const reason = Object.entries(errors).find(([field]) => field.startsWith(prefix))?.[1];
  return reason ? <p className="mt-2 text-sm text-red-500">{reason}</p> : null;
}
