import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import image1 from '@/assets/onboarding/onboarding-1.webp';
import image2 from '@/assets/onboarding/onboarding-2.webp';
import image3 from '@/assets/onboarding/onboarding-3.webp';
import image4 from '@/assets/onboarding/onboarding-4.webp';
import image5 from '@/assets/onboarding/onboarding-5.webp';
import { markOnboardingSeen } from '@/utils/onboarding';

interface Slide {
  title: string[];
  description: string[];
  image: string;
  alt: string;
}

const SLIDES: Slide[] = [
  {
    title: ['아토피,', '이제 혼자서도', '잘 관리할 수 있어요'],
    description: ['아토피 피부염 관리를 위한', '나만의 피부 다이어리, 아토패스와 함께해요.'],
    image: image1,
    alt: '반갑게 인사하는 아토미',
  },
  {
    title: ['지금 내 피부 상태를', '기록해보세요'],
    description: ['증상 정도, 부위별 상태 등을', '간편하게 기록할 수 있어요.'],
    image: image2,
    alt: '부위별 증상 카드를 든 아토미',
  },
  {
    title: ['변화하는 내 피부를', '한눈에 확인해보세요'],
    description: ['기록된 데이터를 그래프로 확인하고,', '특정 날의 생활 습관도 함께 볼 수 있어요.'],
    image: image3,
    alt: '돋보기로 그래프를 보는 아토미',
  },
  {
    title: ['의사에게 전할', '피부 다이어리도 준비돼요'],
    description: ['병원 방문 시, 내 기록을 한눈에 확인할 수 있는', '맞춤형 리포트를 제공해요.'],
    image: image4,
    alt: '피부 다이어리를 든 아토미',
  },
  {
    title: ['건강한 피부를 위한', '첫걸음, 아토패스'],
    description: ['지금 바로 시작하고,', '나만의 피부 관리 루틴을 만들어보세요.'],
    image: image5,
    alt: '하트를 안고 웃는 아토미',
  },
];

/** 온보딩 — /onboarding. 첫 로그인 뒤 한 번 아토패스를 소개한다. 건너뛰거나 끝까지 보면 원래 가려던 화면(기본 홈)으로 간다 */
export default function OnboardingPage() {
  const navigate = useNavigate();
  const from = (useLocation().state as { from?: string } | null)?.from ?? '/';
  const [index, setIndex] = useState(0);
  const slide = SLIDES[index];
  const isLast = index === SLIDES.length - 1;

  const finish = () => {
    markOnboardingSeen();
    navigate(from === '/onboarding' ? '/' : from, { replace: true });
  };

  const dots = (
    <div className={`flex gap-2 ${isLast ? 'justify-center' : ''}`} aria-hidden="true">
      {SLIDES.map((_, i) => (
        <span
          key={i}
          className={`h-2 rounded-full transition-all ${i === index ? 'w-2 bg-brand-600' : 'w-2 bg-brand-200'}`}
        />
      ))}
    </div>
  );

  return (
    <div className="min-h-dvh bg-gradient-to-b from-[#f3faf2] to-[#e8f4e6] font-['Nanum_Square_Round',sans-serif]">
      <div className="mx-auto flex min-h-dvh max-w-md flex-col px-7 pb-8 pt-5">
        <div className="flex h-8 justify-end">
          {!isLast && (
            <button type="button" onClick={finish} className="text-base text-gray-500">
              건너뛰기
            </button>
          )}
        </div>

        <div className={`mt-6 ${isLast ? 'text-center' : ''}`}>
          <h1 className="text-[1.65rem] font-bold leading-snug text-brand-700">
            {slide.title.map((line, i) => (
              <span key={line} className="block">
                {isLast && i === 1 ? (
                  <>
                    첫걸음, <span className="text-brand-800">아토패스</span>
                  </>
                ) : (
                  line
                )}
              </span>
            ))}
          </h1>
          <p className="mt-4 text-base font-normal leading-relaxed text-gray-600">
            {slide.description.map((line) => (
              <span key={line} className="block">
                {line}
              </span>
            ))}
          </p>
        </div>

        {/* 그림 가장자리를 배경색으로 풀어 화면과 이어 보이게 한다 */}
        <div className="flex flex-1 items-center justify-center py-4">
          <img
            key={slide.image}
            src={slide.image}
            alt={slide.alt}
            className="aspect-square w-full max-w-[24rem] scale-125 object-cover [mask-image:radial-gradient(closest-side,#000_66%,transparent_100%)]"
          />
        </div>

        {isLast ? (
          <div className="flex flex-col gap-6">
            {dots}
            <button
              type="button"
              onClick={finish}
              className="rounded-full bg-brand-600 py-4 text-lg font-bold text-white shadow-card"
            >
              시작하기
            </button>
          </div>
        ) : (
          <div className="flex items-center justify-between">
            {dots}
            <button
              type="button"
              aria-label="다음"
              onClick={() => setIndex((n) => n + 1)}
              className="flex size-14 items-center justify-center rounded-full bg-brand-600 text-white shadow-card"
            >
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M5 12h14M13 6l6 6-6 6" />
              </svg>
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
