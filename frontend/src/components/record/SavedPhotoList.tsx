import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { photoApi } from "@/apis/photo";
import type { PhotoRef } from "@/types/record";

interface Props {
  recordId: number;
  photos: PhotoRef[];
  /** 없으면 삭제 버튼을 숨긴다 (보기 모드) */
  onRemove?: (photoId: number) => void;
}

/** 저장된 사진 — 인증 헤더로 blob을 받아 표시한다. 사진을 누르면 화면 가운데에 크게 띄운다 */
export default function SavedPhotoList({ recordId, photos, onRemove }: Props) {
  const [enlarged, setEnlarged] = useState<{ url: string; alt: string } | null>(
    null,
  );
  if (photos.length === 0) return null;
  return (
    <>
      <ul className="flex gap-2 overflow-x-auto">
        {photos.map((p, i) => (
          <li key={p.photoId} className="relative shrink-0">
            <AuthImage
              recordId={recordId}
              photoId={p.photoId}
              alt={`기록 사진 ${i + 1}`}
              onOpen={(url, alt) => setEnlarged({ url, alt })}
            />
            {onRemove && (
              <button
                type="button"
                onClick={() => onRemove(p.photoId)}
                className="absolute -right-1 -top-1 size-6 rounded-full bg-gray-800 text-sm text-white"
                aria-label={`사진 ${i + 1} 삭제`}
              >
                ×
              </button>
            )}
          </li>
        ))}
      </ul>
      {enlarged && (
        <PhotoLightbox
          url={enlarged.url}
          alt={enlarged.alt}
          onClose={() => setEnlarged(null)}
        />
      )}
    </>
  );
}

/** 사진을 화면 가운데에 크게 보여 준다. 바깥을 누르거나 X를 누르거나 Esc를 누르면 닫힌다 */
function PhotoLightbox({
  url,
  alt,
  onClose,
}: {
  url: string;
  alt: string;
  onClose: () => void;
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return createPortal(
    <div
      role="dialog"
      aria-modal="true"
      aria-label={alt}
      onClick={onClose}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4"
    >
      <button
        type="button"
        onClick={onClose}
        aria-label="닫기"
        className="absolute right-4 top-4 flex size-10 items-center justify-center rounded-full bg-white/90 text-2xl text-gray-800"
      >
        ×
      </button>
      <img
        src={url}
        alt={alt}
        onClick={(e) => e.stopPropagation()}
        className="max-h-[85dvh] max-w-full rounded-xl object-contain shadow-lg"
      />
    </div>,
    document.body,
  );
}

function AuthImage({
  recordId,
  photoId,
  alt,
  onOpen,
}: {
  recordId: number;
  photoId: number;
  alt: string;
  onOpen: (url: string, alt: string) => void;
}) {
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
    <button
      type="button"
      onClick={() => onOpen(url, alt)}
      aria-label={`${alt} 크게 보기`}
      className="block"
    >
      <img src={url} alt={alt} className="size-20 rounded-lg object-cover" />
    </button>
  ) : (
    <div
      className="size-20 animate-pulse rounded-lg bg-gray-100"
      aria-label={alt}
    />
  );
}
