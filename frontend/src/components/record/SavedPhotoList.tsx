import { useEffect, useState } from 'react';
import { photoApi } from '@/apis/photo';
import type { PhotoRef } from '@/types/record';

interface Props {
  recordId: number;
  photos: PhotoRef[];
  onRemove: (photoId: number) => void;
}

/** 저장된 사진 — 인증 헤더로 blob을 받아 표시한다 */
export default function SavedPhotoList({ recordId, photos, onRemove }: Props) {
  if (photos.length === 0) return null;
  return (
    <ul className="flex gap-2 overflow-x-auto">
      {photos.map((p, i) => (
        <li key={p.photoId} className="relative shrink-0">
          <AuthImage recordId={recordId} photoId={p.photoId} alt={`기록 사진 ${i + 1}`} />
          <button
            type="button"
            onClick={() => onRemove(p.photoId)}
            className="absolute -right-1 -top-1 size-6 rounded-full bg-gray-800 text-sm text-white"
            aria-label={`사진 ${i + 1} 삭제`}
          >
            ×
          </button>
        </li>
      ))}
    </ul>
  );
}

function AuthImage({ recordId, photoId, alt }: { recordId: number; photoId: number; alt: string }) {
  const [url, setUrl] = useState<string | null>(null);

  useEffect(() => {
    let objectUrl: string | null = null;
    let cancelled = false;
    photoApi
      .getObjectUrl(recordId, photoId)
      .then((u) => {
        if (cancelled) URL.revokeObjectURL(u);
        else setUrl((objectUrl = u));
      })
      .catch(() => setUrl(null));
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [recordId, photoId]);

  return url ? (
    <img src={url} alt={alt} className="size-20 rounded-lg object-cover" />
  ) : (
    <div className="size-20 animate-pulse rounded-lg bg-gray-100" aria-label={alt} />
  );
}
