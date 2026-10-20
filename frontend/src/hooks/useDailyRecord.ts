import { useCallback, useEffect, useState } from 'react';
import { chatbotApi } from '@/apis/chatbot';
import { ApiError } from '@/apis/client';
import { photoApi } from '@/apis/photo';
import { recordApi } from '@/apis/record';
import type { PhotoRef, RecordInput } from '@/types/record';
import { dailyOnlySkinSigns } from '@/utils/bodyMap';
import { chatSessionStore } from './useRecordDraft';

/**
 * - edit: 이미 저장된 기록 → PUT /records/{id}
 * - chatDraft: 챗봇 세션의 초안(GET /chatbot/sessions/{id}/draft)으로 채움 → POST /chatbot/sessions/{id}/records (confirmed: true)
 * - new: 기록도 세션도 없음 → 직접 입력, POST /records
 */
export type DailyRecordMode = 'edit' | 'chatDraft' | 'new';

const emptyRecord = (recordDate: string): RecordInput => ({
  recordDate,
  skinConditions: [],
  painIndex: null,
  sleepHours: null,
  medication: null,
  skinSigns: [],
  lifestyle: null,
  notes: null,
});

/**
 * 챗봇 세션의 초안을 폼 값으로 가져온다.
 * - 'closed': 이미 저장된 세션(409 SESSION_CLOSED) → GET /records로 조회해야 함
 * - null: 세션이 없거나 만료됨(SESSION_NOT_FOUND) → 직접 입력
 */
async function loadChatDraft(sessionId: string) {
  return chatbotApi
    .getDraft(sessionId)
    .then(({ draft }) => ({
      skinConditions: draft.skinConditions ?? [],
      painIndex: draft.painIndex ?? null,
      sleepHours: draft.sleepHours ?? null,
      medication: draft.medication ?? null,
      skinSigns: draft.skinSigns ?? [],
      lifestyle: draft.lifestyle ?? null,
      notes: draft.notes ?? null,
    }))
    .catch((e: unknown) => {
      if (e instanceof ApiError && e.code === 'SESSION_CLOSED') return 'closed' as const;
      if (e instanceof ApiError && e.code === 'SESSION_NOT_FOUND') return null;
      throw e;
    });
}

export function useDailyRecord(recordDate: string) {
  const [mode, setMode] = useState<DailyRecordMode>('new');
  const [recordId, setRecordId] = useState<number | null>(null);
  const [form, setForm] = useState<RecordInput>(() => emptyRecord(recordDate));
  const [photos, setPhotos] = useState<PhotoRef[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const summary = await recordApi.findByDate(recordDate);
      if (summary) {
        const detail = await recordApi.getDetail(summary.recordId);
        const { recordId, photos, sleepScore: _s, createdAt: _c, updatedAt: _u, ...input } = detail;
        setMode('edit');
        setRecordId(recordId);
        // 서버의 하루 증상에는 부위 증상이 합쳐져 있다 — 부위 증상을 빼 두어야 부위에서 지운 증상이 다시 저장되지 않는다
        setForm({ ...input, skinSigns: dailyOnlySkinSigns(input) });
        setPhotos(photos ?? []);
        return;
      }

      setRecordId(null);
      setPhotos([]);
      const sessionId = chatSessionStore.get(recordDate);
      const draft = sessionId ? await loadChatDraft(sessionId) : null;
      if (draft === 'closed') {
        // 세션은 이미 기록으로 저장됨 → 저장된 기록을 다시 조회한다
        chatSessionStore.clear(recordDate);
        await load();
        return;
      }
      if (draft) {
        setMode('chatDraft');
        setForm({ recordDate, ...draft });
      } else {
        if (sessionId) chatSessionStore.clear(recordDate);
        setMode('new');
        setForm(emptyRecord(recordDate));
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '기록을 불러오지 못했어요.');
    } finally {
      setLoading(false);
    }
  }, [recordDate]);

  useEffect(() => {
    void load();
  }, [load]);

  /**
   * 저장 후 대기 중인 사진(챗봇에서 추가한 사진 포함)을 업로드한다.
   * 기록 저장은 성공했는데 사진 업로드만 실패할 수 있으므로, 실패한 사진을 돌려준다.
   */
  const save = useCallback(
    async (pendingPhotos: File[]): Promise<File[]> => {
      let savedId = recordId;
      if (mode === 'edit' && recordId) {
        await recordApi.update(recordId, form);
      } else if (mode === 'chatDraft') {
        const sessionId = chatSessionStore.get(recordDate);
        if (!sessionId) throw new Error('대화 세션 정보가 없어요. 다시 시도해 주세요.');
        const { recordDate: _d, ...record } = form;
        savedId = (await chatbotApi.saveRecord(sessionId, { recordDate, confirmed: true, record })).recordId;
        chatSessionStore.clear(recordDate);
      } else {
        savedId = (await recordApi.create(form)).recordId;
      }

      const failed: File[] = [];
      if (savedId) {
        for (const file of pendingPhotos) {
          try {
            await photoApi.upload(savedId, file);
          } catch {
            failed.push(file);
          }
        }
      }
      await load();
      return failed;
    },
    [form, mode, recordDate, recordId, load],
  );

  const remove = useCallback(async () => {
    if (!recordId) return;
    await recordApi.remove(recordId); // 연결된 사진도 함께 삭제됨
    await load();
  }, [recordId, load]);

  const removePhoto = useCallback(
    async (photoId: number) => {
      if (!recordId) return;
      await photoApi.remove(recordId, photoId);
      setPhotos((prev) => prev.filter((p) => p.photoId !== photoId));
    },
    [recordId],
  );

  return { mode, recordId, form, setForm, photos, loading, error, save, remove, removePhoto, reload: load };
}
