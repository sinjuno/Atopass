import confusedImage from '@/assets/atomi/confused.png';
import defaultImage from '@/assets/atomi/default.png';
import recordingImage from '@/assets/atomi/recording.png';
import worriedImage from '@/assets/atomi/worried.png';
import type { AtomiMood } from '@/utils/atomiMood';

const IMAGES: Record<AtomiMood, { src: string; alt: string }> = {
  default: { src: defaultImage, alt: '웃고 있는 아토미' },
  worried: { src: worriedImage, alt: '걱정하는 아토미' },
  recording: { src: recordingImage, alt: '기록하는 아토미' },
  confused: { src: confusedImage, alt: '고민하는 아토미' },
};

/** 챗봇 캐릭터 아토미의 프로필 이미지 — 상태(mood)에 따라 표정이 바뀐다 */
export default function AtomiAvatar({ mood = 'default' }: { mood?: AtomiMood }) {
  const { src, alt } = IMAGES[mood];
  return <img src={src} alt={alt} className="h-10 w-10 shrink-0 rounded-full bg-white object-cover" />;
}
