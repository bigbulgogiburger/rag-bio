"use client";

import LoginForm from "@/components/auth/LoginForm";

export default function LoginPage() {
  return (
    <div className="flex min-h-[100dvh] -mx-4 -my-6 sm:-mx-6 sm:-my-10">
      {/* 좌측 브랜딩 패널 -- lg만 표시 */}
      <div className="hidden lg:flex lg:w-1/2 lg:flex-col lg:justify-between bg-[hsl(var(--foreground))] text-[hsl(var(--background))] p-12">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary shadow-lg">
            <svg
              width="20"
              height="20"
              viewBox="0 0 18 18"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
              aria-hidden="true"
            >
              <path
                d="M3 4.5C3 4.5 5 3 9 3C13 3 15 4.5 15 4.5V14.5C15 14.5 13 13 9 13C5 13 3 14.5 3 14.5V4.5Z"
                stroke="white"
                strokeWidth="1.5"
                strokeLinejoin="round"
              />
              <path d="M9 3V13" stroke="white" strokeWidth="1.5" />
              <circle cx="6" cy="7.5" r="1" fill="white" />
              <circle cx="12" cy="7.5" r="1" fill="white" />
            </svg>
          </div>
          <span className="text-lg font-bold">Bio-Rad</span>
        </div>

        <div className="space-y-6">
          <h1 className="text-4xl font-bold tracking-tight leading-tight">
            고객 문의에<br />더 빠르고 정확하게<br />대응하세요
          </h1>
          <p className="text-[hsl(var(--background))]/60 text-lg max-w-md leading-relaxed">
            RAG 기반 파이프라인이 기술 문서를 분석하고 전문적인 답변 초안을 실시간으로 생성합니다.
          </p>
        </div>

        <div className="flex items-center gap-8 text-sm text-[hsl(var(--background))]/40">
          <div>
            <p className="text-2xl font-bold text-[hsl(var(--background))]/80 tabular-nums">87%</p>
            <p>자동화율</p>
          </div>
          <div className="h-8 w-px bg-[hsl(var(--background))]/10" />
          <div>
            <p className="text-2xl font-bold text-[hsl(var(--background))]/80 tabular-nums">3.2초</p>
            <p>평균 생성</p>
          </div>
          <div className="h-8 w-px bg-[hsl(var(--background))]/10" />
          <div>
            <p className="text-2xl font-bold text-[hsl(var(--background))]/80 tabular-nums">98.5%</p>
            <p>인용 정확도</p>
          </div>
        </div>
      </div>

      {/* 우측 로그인 폼 */}
      <div className="flex flex-1 flex-col items-center justify-center px-6 py-12">
        <div className="w-full max-w-sm space-y-8">
          {/* 모바일 로고 */}
          <div className="text-center lg:hidden">
            <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-primary shadow-lg">
              <svg
                width="24"
                height="24"
                viewBox="0 0 18 18"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
              >
                <path
                  d="M3 4.5C3 4.5 5 3 9 3C13 3 15 4.5 15 4.5V14.5C15 14.5 13 13 9 13C5 13 3 14.5 3 14.5V4.5Z"
                  stroke="white"
                  strokeWidth="1.5"
                  strokeLinejoin="round"
                />
                <path d="M9 3V13" stroke="white" strokeWidth="1.5" />
                <circle cx="6" cy="7.5" r="1" fill="white" />
                <circle cx="12" cy="7.5" r="1" fill="white" />
              </svg>
            </div>
            <h1 className="text-2xl font-bold tracking-tight">Bio-Rad CS 대응 허브</h1>
            <p className="text-sm text-muted-foreground mt-2">계정 정보를 입력하여 로그인하세요</p>
          </div>
          {/* 데스크톱 헤더 */}
          <div className="hidden lg:block">
            <h2 className="text-2xl font-bold tracking-tight">로그인</h2>
            <p className="text-sm text-muted-foreground mt-2">계정 정보를 입력하여 시작하세요</p>
          </div>
          <LoginForm />
          <p className="text-center text-xs text-muted-foreground">v3.0 &middot; Sprint 14</p>
        </div>
      </div>
    </div>
  );
}
