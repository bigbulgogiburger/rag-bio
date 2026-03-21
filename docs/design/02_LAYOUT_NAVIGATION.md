# 02. 레이아웃 & 네비게이션 현대화

## 현재 분석

### Header (`layout.tsx:30-58`)
```
현재: sticky top-0 border-b bg-card/80 backdrop-blur-md
구조: [Logo + 텍스트] ──────────────── [Nav Links] [ThemeToggle]
```

**문제점:**
- 평범한 상단 바 — SaaS 템플릿 느낌
- Nav 링크가 header 안에 밀착 → 시각적 분리 부족
- 로고 아이콘이 `from-blue-500 to-blue-600` 하드코딩

### Footer (`layout.tsx:64-95`)
```
현재: bg-slate-900 dark:bg-slate-950 text-slate-400
```

**문제점:**
- CSS 변수 미사용 (유일한 하드코딩 영역)
- `text-slate-500 dark:text-slate-600` 등 인라인 색상
- 모바일에서 `hidden` — 모바일 사용자는 footer 정보 접근 불가

### Mobile Bottom Nav (`app-shell-nav.tsx:91-115`)
```
현재: fixed bottom-0 border-t bg-card/95 backdrop-blur-md
구조: [대시보드] [문의목록] [문의작성] [지식기반]
```

**장점:** safe-area-inset, 44px 터치 타겟 준수
**문제점:**
- 활성 상태가 `text-primary`만 — 시각적 피드백 약함
- 아이콘 크기 `h-5 w-5`가 모바일에서 다소 작음

---

## 변경 제안

### 1. Header → Floating Glass Pill

```
변경 후 구조:

┌──────────────────────────────────────────────────────────┐
│  margin-top: 12px                                         │
│  ┌────────────────────────────────────────────────────┐   │
│  │  [Logo]  ·  대시보드 │ 문의목록 │ 문의작성 │ 지식기반  [🌙] │
│  └────────────────────────────────────────────────────┘   │
│  mx-auto max-w-5xl rounded-2xl                            │
│  bg-card/70 backdrop-blur-xl border border-border/50      │
│  shadow-brand                                             │
└──────────────────────────────────────────────────────────┘
```

```tsx
// layout.tsx — Header 변경
<header className="sticky top-0 z-50 pt-3 pb-2 px-4">
  <div className="mx-auto max-w-5xl">
    <nav className="flex items-center justify-between rounded-2xl
                    border border-border/50 bg-card/70 backdrop-blur-xl
                    px-4 py-2 shadow-brand">
      {/* Logo */}
      <Link href="/dashboard" className="flex items-center gap-2.5 ...">
        <div className="flex h-8 w-8 items-center justify-center
                        rounded-lg bg-primary shadow-sm">
          {/* SVG 아이콘 — 하드코딩 blue 제거, bg-primary 사용 */}
        </div>
        <span className="text-sm font-bold tracking-tight">
          Bio-Rad <span className="text-primary font-extrabold">CS</span>
        </span>
      </Link>

      {/* Desktop Nav */}
      <div className="hidden md:flex items-center gap-1">
        <AppShellNav />
      </div>

      {/* Actions */}
      <div className="flex items-center gap-2">
        <ThemeToggle />
      </div>
    </nav>
  </div>
</header>
```

**핵심 변경:**
- `border-b` 직선 → `rounded-2xl` 부유 형태
- `pt-3`으로 상단 여백 → 페이지와 분리감
- 로고 `from-blue-500 to-blue-600` → `bg-primary` (토큰화)
- 서브타이틀 `RAG 기반 고객문의 대응 워크스페이스` 제거 (반복 정보)

### 2. Nav 링크 활성 상태 강화

```tsx
// 현재
const activeClass = "bg-primary/10 text-primary font-semibold";

// 변경
const activeClass = cn(
  "bg-primary/10 text-primary font-semibold",
  "shadow-[inset_0_-2px_0_hsl(var(--primary))]"  // 하단 인디케이터
);
```

### 3. Footer 토큰 통합

```tsx
// 현재: 하드코딩
<footer className="bg-slate-900 text-slate-400 dark:bg-slate-950 dark:text-slate-500">

// 변경: CSS 변수 사용
<footer className="bg-foreground/95 text-background/60">
  {/* 또는 신규 토큰 추가 */}
</footer>
```

**Footer 전용 토큰 추가 (globals.css):**
```css
:root {
  --footer-bg: 224 30% 12%;
  --footer-fg: 220 13% 65%;
  --footer-muted: 220 13% 40%;
  --footer-border: 220 13% 20%;
}
.dark {
  --footer-bg: 224 30% 6%;
  --footer-fg: 220 13% 55%;
  --footer-muted: 220 13% 35%;
  --footer-border: 220 13% 15%;
}
```

**Footer 인라인 색상 제거:**
```diff
- <strong className="text-sm text-slate-200 dark:text-slate-300">
+ <strong className="text-sm text-[hsl(var(--footer-fg))] brightness-125">

- <span className="text-slate-500 dark:text-slate-600">
+ <span className="text-[hsl(var(--footer-muted))]">
```

### 4. Mobile Bottom Nav 개선

```tsx
// 변경: 활성 상태에 배경 + 인디케이터 추가
<Link
  href={href}
  className={cn(
    "flex flex-1 flex-col items-center gap-1 px-2 py-2.5",
    "text-[0.65rem] font-medium transition-all",
    isActive(href, pathname)
      ? "text-primary"
      : "text-muted-foreground"
  )}
>
  <div className={cn(
    "flex h-8 w-8 items-center justify-center rounded-xl transition-all",
    isActive(href, pathname) && "bg-primary/10 scale-110"
  )}>
    <Icon className="h-5 w-5" />
  </div>
  <span>{shortLabel}</span>
</Link>
```

**핵심 변경:**
- 아이콘을 `div`로 감싸 배경 처리 → Material Design 3 스타일
- 활성 시 `bg-primary/10 scale-110` → 시각적 피드백 강화
- `py-3` → `py-2.5` (아이콘 배경이 있으므로 내부 패딩 감소)

### 5. 페이지 컨테이너 여백 조정

```tsx
// 현재
<main className="mx-auto max-w-7xl px-4 py-4 sm:px-6 sm:py-8 flex-1 w-full pb-20 md:pb-0">

// 변경: Floating header와의 간격 + 더 넉넉한 호흡
<main className="mx-auto max-w-7xl px-4 py-6 sm:px-6 sm:py-10 flex-1 w-full pb-24 md:pb-8">
```

---

## 반응형 브레이크포인트 전략

| 브레이크포인트 | 레이아웃 | Nav |
|---------------|----------|-----|
| `< 768px` | 1열 풀와이드 | Bottom Nav (4항목) |
| `768px ~ 1024px` | 2열 가능 | Header 내 링크 |
| `> 1024px` | 자유 그리드 | Floating Glass Pill |

---

## 예상 영향

- Header/Footer 변경은 `layout.tsx` 1파일 수정
- Nav 변경은 `app-shell-nav.tsx` 1파일 수정
- 모든 페이지에 자동 반영 (레이아웃 공유)
- Dark 모드는 CSS 변수 기반이므로 자동 호환
