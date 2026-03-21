# 08. 로그인 페이지 현대화

**경로:** `/login`
**파일:** `src/app/login/page.tsx` + `src/components/auth/LoginForm.tsx`

---

## 현재 상태

### 레이아웃
```
(Header 포함)

          ┌──────────────────────┐
          │      [BR 아이콘]      │
          │                      │
          │  Bio-Rad CS 대응 허브  │
          │  계정 정보를 입력하여   │
          │  로그인하세요          │
          │                      │
          │  [이메일 Input]       │
          │  [비밀번호 Input]     │
          │  [로그인 버튼]        │
          └──────────────────────┘
```

### 강점
- Card 컴포넌트 사용
- 중앙 정렬 양호
- 로고 아이콘 포함

### 문제점

1. **단순한 디자인**: 기본 Card에 form만 — 브랜딩 기회 미활용
2. **Header가 보임**: 로그인 페이지에서도 Nav가 표시됨 (의미 없음)
3. **좌우 빈 공간**: `max-w-md` 카드만 중앙 — 넓은 화면에서 허전
4. **시각적 앵커 없음**: 배경이 그냥 `--background` 색상
5. **에러 표시**: LoginForm 내 에러 상태 확인 필요

---

## 현대화 설계

### 1. 전체 레이아웃 — Split Screen (데스크톱)

```
Desktop:
┌────────────────────┬───────────────────────┐
│                    │                       │
│  (브랜딩 패널)      │     [BR 아이콘]        │
│                    │                       │
│  Bio-Rad           │  Bio-Rad CS 대응 허브   │
│  Customer          │  계정 정보를 입력하여    │
│  Support Hub       │  로그인하세요           │
│                    │                       │
│  RAG 기반           │  [이메일 Input]        │
│  CS 대응 시스템      │  [비밀번호 Input]      │
│                    │  [로그인 버튼]          │
│  ──────────────    │                       │
│  신속한 답변 생성    │  v3.0 · Sprint 14     │
│  87% 자동화율       │                       │
│                    │                       │
└────────────────────┴───────────────────────┘

Mobile:
┌───────────────────────┐
│                       │
│     [BR 아이콘]        │
│                       │
│  Bio-Rad CS 대응 허브   │
│  계정 정보를 입력하여    │
│  로그인하세요           │
│                       │
│  [이메일 Input]        │
│  [비밀번호 Input]      │
│  [로그인 버튼]          │
│                       │
└───────────────────────┘
```

### 2. 구현 코드

```tsx
export default function LoginPage() {
  return (
    <div className="min-h-[100dvh] flex">
      {/* 좌측 브랜딩 패널 — 데스크톱만 */}
      <div className="hidden lg:flex lg:w-1/2 lg:flex-col lg:justify-between
                      bg-foreground text-background p-12">
        <div>
          {/* 로고 */}
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center
                            rounded-xl bg-primary shadow-lg">
              {/* SVG 아이콘 */}
            </div>
            <span className="text-lg font-bold">Bio-Rad</span>
          </div>
        </div>

        <div className="space-y-6">
          <h1 className="text-4xl font-bold tracking-tight leading-tight">
            고객 문의에<br />
            더 빠르고 정확하게<br />
            대응하세요
          </h1>
          <p className="text-background/60 text-lg max-w-md leading-relaxed">
            RAG 기반 파이프라인이 기술 문서를 분석하고
            전문적인 답변 초안을 실시간으로 생성합니다.
          </p>
        </div>

        <div className="flex items-center gap-8 text-sm text-background/40">
          <div>
            <p className="text-2xl font-bold text-background/80 tabular-nums">87%</p>
            <p>자동화율</p>
          </div>
          <div className="h-8 w-px bg-background/10" />
          <div>
            <p className="text-2xl font-bold text-background/80 tabular-nums">3.2초</p>
            <p>평균 답변 생성</p>
          </div>
          <div className="h-8 w-px bg-background/10" />
          <div>
            <p className="text-2xl font-bold text-background/80 tabular-nums">98.5%</p>
            <p>인용 정확도</p>
          </div>
        </div>
      </div>

      {/* 우측 로그인 폼 */}
      <div className="flex flex-1 flex-col items-center justify-center px-6 py-12">
        <div className="w-full max-w-sm space-y-8">
          {/* 모바일 로고 */}
          <div className="text-center lg:hidden">
            <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center
                            rounded-2xl bg-primary shadow-lg">
              {/* SVG 아이콘 */}
            </div>
            <h1 className="text-2xl font-bold tracking-tight">
              Bio-Rad CS 대응 허브
            </h1>
            <p className="text-sm text-muted-foreground mt-2">
              계정 정보를 입력하여 로그인하세요
            </p>
          </div>

          {/* 데스크톱 헤더 */}
          <div className="hidden lg:block">
            <h2 className="text-2xl font-bold tracking-tight">
              로그인
            </h2>
            <p className="text-sm text-muted-foreground mt-2">
              계정 정보를 입력하여 시작하세요
            </p>
          </div>

          {/* 로그인 폼 */}
          <LoginForm />

          {/* 푸터 */}
          <p className="text-center text-xs text-muted-foreground">
            v3.0 · Sprint 14
          </p>
        </div>
      </div>
    </div>
  );
}
```

### 3. LoginForm 내부 개선

```tsx
{/* 입력 필드 — 라벨 + 아이콘 */}
<div className="space-y-4">
  <div className="space-y-2">
    <label className="text-sm font-medium">이메일</label>
    <div className="relative">
      <MailIcon className="absolute left-3 top-1/2 -translate-y-1/2
                           h-4 w-4 text-muted-foreground" />
      <input
        type="email"
        placeholder="name@biorad.com"
        className="w-full rounded-xl border border-input bg-transparent
                   pl-10 pr-4 py-2.5 text-sm shadow-sm
                   focus-visible:ring-2 focus-visible:ring-ring/50
                   focus-visible:border-primary transition-shadow"
      />
    </div>
  </div>

  <div className="space-y-2">
    <label className="text-sm font-medium">비밀번호</label>
    <div className="relative">
      <LockIcon className="absolute left-3 top-1/2 -translate-y-1/2
                            h-4 w-4 text-muted-foreground" />
      <input
        type="password"
        placeholder="••••••••"
        className="w-full rounded-xl border border-input bg-transparent
                   pl-10 pr-4 py-2.5 text-sm shadow-sm
                   focus-visible:ring-2 focus-visible:ring-ring/50
                   focus-visible:border-primary transition-shadow"
      />
    </div>
  </div>
</div>

{/* 로그인 버튼 */}
<Button
  type="submit"
  size="lg"
  className="w-full rounded-xl text-base font-semibold
             shadow-lg shadow-primary/20 hover:shadow-xl
             hover:shadow-primary/30 transition-all"
  disabled={isSubmitting}
>
  {isSubmitting ? (
    <span className="flex items-center gap-2">
      <Loader2 className="h-4 w-4 animate-spin" />
      로그인 중...
    </span>
  ) : (
    "로그인"
  )}
</Button>

{/* 에러 메시지 */}
{error && (
  <div className="rounded-xl border border-destructive/30 bg-destructive/5
                  px-4 py-3 text-sm text-destructive flex items-center gap-2
                  animate-in fade-in slide-in-from-top-2 duration-200">
    <AlertCircle className="h-4 w-4 shrink-0" />
    {error}
  </div>
)}
```

### 4. Header 숨김 처리

로그인 페이지에서는 Header/Footer/BottomNav를 숨기는 것이 좋음:

```tsx
// layout.tsx에서 pathname 기반 조건부 렌더링
// 또는 login/page.tsx에서 별도 layout 사용

// 방법 1: login 폴더에 layout.tsx 추가
// src/app/login/layout.tsx
export default function LoginLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
  // Header, Footer, BottomNav 미포함
}
```

**주의:** Next.js App Router에서 중첩 레이아웃은 부모를 대체하지 않음.
→ root layout에서 `usePathname()`으로 `/login`일 때 header/footer 숨김 처리가 더 실용적.

---

## 모바일 레이아웃

| 요소 | 데스크톱 | 모바일 |
|------|---------|--------|
| 브랜딩 패널 | 좌측 50% | 숨김 |
| 로고 | 숨김 (브랜딩 패널에) | 중앙 표시 |
| 폼 위치 | 우측 50% 중앙 | 전체 중앙 |
| 입력 필드 | `rounded-xl` + 아이콘 | 동일 |
| 하단 정보 | 통계 수치 | 버전 정보만 |

---

## 접근성

- [ ] `autofocus` on email input
- [ ] `autocomplete="email"` / `autocomplete="current-password"`
- [ ] 에러 메시지에 `role="alert" aria-live="assertive"`
- [ ] 폼에 `aria-label="로그인 폼"`
- [ ] 로딩 상태에 `aria-busy="true"`
