import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';

/**
 * 챗봇 → 일일 총정리로 넘기는 임시 상태.
 * 사진 업로드 API는 recordId가 필요하므로, 챗봇 대화 중 추가한 사진은 여기 보관했다가
 * 총정리에서 기록이 저장된 뒤 업로드한다 (화면 명세서 4-1).
 * File 객체라 새로고침하면 사라진다.
 */
interface RecordDraftState {
  pendingPhotos: Record<string, File[]>; // key: recordDate
  addPhotos: (date: string, files: File[]) => void;
  removePhoto: (date: string, index: number) => void;
  clearPhotos: (date: string) => void;
}

const RecordDraftContext = createContext<RecordDraftState | null>(null);

export function RecordDraftProvider({ children }: { children: ReactNode }) {
  const [pendingPhotos, setPendingPhotos] = useState<Record<string, File[]>>({});

  const addPhotos = useCallback((date: string, files: File[]) => {
    setPendingPhotos((prev) => ({ ...prev, [date]: [...(prev[date] ?? []), ...files] }));
  }, []);

  const removePhoto = useCallback((date: string, index: number) => {
    setPendingPhotos((prev) => ({ ...prev, [date]: (prev[date] ?? []).filter((_, i) => i !== index) }));
  }, []);

  const clearPhotos = useCallback((date: string) => {
    setPendingPhotos(({ [date]: _removed, ...rest }) => rest);
  }, []);

  const value = useMemo(
    () => ({ pendingPhotos, addPhotos, removePhoto, clearPhotos }),
    [pendingPhotos, addPhotos, removePhoto, clearPhotos],
  );

  return <RecordDraftContext.Provider value={value}>{children}</RecordDraftContext.Provider>;
}

export function useRecordDraft(date: string) {
  const ctx = useContext(RecordDraftContext);
  if (!ctx) throw new Error('useRecordDraft는 RecordDraftProvider 안에서만 쓸 수 있어요.');
  return {
    photos: ctx.pendingPhotos[date] ?? [],
    addPhotos: (files: File[]) => ctx.addPhotos(date, files),
    removePhoto: (index: number) => ctx.removePhoto(date, index),
    clearPhotos: () => ctx.clearPhotos(date),
  };
}

// ---------- 날짜별 챗봇 세션 ID (화면 재진입 시 대화 복원용) ----------
// 세션은 날짜 하나당 하나 (API 명세서 5. 세부 사항). 비밀정보가 아니라 sessionStorage에 보관한다.
const sessionKey = (date: string) => `atopass:chat-session:${date}`;

export const chatSessionStore = {
  get: (date: string) => {
    try {
      return sessionStorage.getItem(sessionKey(date));
    } catch {
      return null;
    }
  },
  set: (date: string, sessionId: string) => {
    try {
      sessionStorage.setItem(sessionKey(date), sessionId);
    } catch {
      /* 저장 실패 시 재진입 때 새 세션으로 시작 */
    }
  },
  clear: (date: string) => {
    try {
      sessionStorage.removeItem(sessionKey(date));
    } catch {
      /* noop */
    }
  },
};
