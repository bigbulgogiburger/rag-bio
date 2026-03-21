# 01. 디자인 시스템 통합

## 현재 분석

### 컬러 토큰 (globals.css)
현재 HSL 변수 기반으로 잘 구축되어 있으나 몇 가지 불일치:

| 문제 | 위치 | 설명 |
|------|------|------|
| Footer 하드코딩 | `layout.tsx:64` | `bg-slate-900 dark:bg-slate-950` — CSS 변수 미사용 |
| RAG 메트릭 카드 | `dashboard/page.tsx:419` | `var(--color-text-secondary)` 존재하지 않는 변수 참조 |
| 일관성 없는 카드 | 여러 곳 | Card 컴포넌트 vs 인라인 `rounded-xl border bg-card` 혼용 |
| Accent = Secondary | `globals.css:19-20` | `--accent`와 `--secondary`가 동일값 — 역할 분리 필요 |

### 타이포그래피
- Pretendard 로딩은 양호하나 `Noto Sans KR`이 fallback에 포함되어 있음 (제거 권장)
- 페이지 타이틀이 모두 `text-xl` — 위계 부족
- `break-keep-all` 미적용 (한국어 줄바꿈 최적화 누락)

### 스페이싱
- `space-y-6` 패턴이 일관적이나, 섹션 간 호흡이 부족
- 모바일에서 `p-4`, 데스크톱에서 `p-6` 반복 — 토큰화 필요

---

## 변경 제안

### 1. 컬러 토큰 현대화

```css
/* globals.css — :root 변경 사항 */
:root {
  /* ── 기존 유지 (값 미세 조정) ── */
  --background: 220 14% 96%;          /* 더 뉴트럴한 배경 */
  --foreground: 224 71% 8%;           /* 더 깊은 텍스트 */
  --card: 0 0% 100%;
  --card-foreground: 224 71% 8%;

  --primary: 224 71% 48%;             /* Indigo-Blue (더 세련됨) */
  --primary-foreground: 0 0% 100%;

  --secondary: 220 13% 95%;           /* 미세 배경 차이 */
  --secondary-foreground: 224 71% 8%;

  --accent: 224 71% 95%;              /* Primary의 극히 연한 버전 */
  --accent-foreground: 224 71% 30%;   /* Accent 전경색 분리 */

  --muted: 220 13% 95%;
  --muted-foreground: 220 9% 46%;     /* 더 부드러운 보조 텍스트 */

  --border: 220 13% 91%;
  --input: 220 13% 91%;
  --ring: 224 71% 48%;

  /* ── 신규 추가 토큰 ── */
  --surface-elevated: 0 0% 100%;      /* 부유 카드 배경 */
  --surface-sunken: 220 14% 93%;      /* 함몰 영역 배경 */
  --overlay: 224 71% 4%;              /* 모달 오버레이 */
  --shadow-color: 224 71% 4%;         /* 틴티드 그림자 */
}
```

### 2. 타이포그래피 스케일

```css
/* globals.css에 추가 */
@layer base {
  /* 한국어 최적화 */
  :root {
    --font-display: 'Pretendard', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
    --font-body: 'Pretendard', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
    --font-mono: 'JetBrains Mono', ui-monospace, monospace;
  }

  body {
    word-break: keep-all;              /* 한국어 줄바꿈 최적화 */
    overflow-wrap: break-word;
  }
}
```

| 역할 | 현재 | 변경 후 |
|------|------|---------|
| 페이지 타이틀 | `text-xl font-semibold` | `text-2xl sm:text-3xl font-bold` |
| 섹션 헤더 | `text-base font-semibold` | `text-lg font-semibold` |
| 카드 라벨 | `text-xs uppercase tracking-wide` | 유지 (양호) |
| 카드 값 | `text-2xl font-bold` | 유지 (양호) |
| 본문 | `text-sm` | `text-sm leading-relaxed` |
| 캡션 | `text-xs text-muted-foreground` | `text-xs text-muted-foreground leading-normal` |

### 3. 카드 현대화 — Double-Bezel Architecture

현재 모든 카드가 `rounded-xl border bg-card shadow-sm` 단일 패턴.

```
변경: 주요 카드에 2중 구조 적용

외부 쉘: rounded-2xl bg-gradient-to-b from-white/80 to-white/40 p-px
         dark:from-white/10 dark:to-white/5
내부 코어: rounded-[calc(1rem-1px)] bg-card p-6
         shadow-[inset_0_1px_0_rgba(255,255,255,0.1)]
```

적용 범위:
- **Double-Bezel**: 메트릭 카드, 주요 정보 카드 (대시보드, KB 통계)
- **Standard**: 목록 카드, 테이블 래퍼 (기존 패턴 유지)
- **Flat**: 탭 내부 콘텐츠, 인라인 정보 (border만)

### 4. 그림자 틴팅

```css
/* 현재: 무채색 그림자 */
shadow-sm  /* rgba(0,0,0,0.05) */

/* 변경: 브랜드 틴티드 그림자 */
.shadow-brand {
  box-shadow:
    0 1px 2px hsl(var(--shadow-color) / 0.04),
    0 4px 12px hsl(var(--shadow-color) / 0.06);
}

.shadow-brand-lg {
  box-shadow:
    0 4px 6px hsl(var(--shadow-color) / 0.04),
    0 12px 32px hsl(var(--shadow-color) / 0.08);
}
```

### 5. 포커스 상태 통일

```css
/* 현재 — 양호하나 미세 조정 */
*:focus-visible {
  outline: 2px solid hsl(var(--ring));
  outline-offset: 2px;
  border-radius: calc(var(--radius) - 4px);
}

/* 변경: 더 부드러운 포커스 링 */
*:focus-visible {
  outline: 2px solid hsl(var(--ring) / 0.5);
  outline-offset: 2px;
  border-radius: calc(var(--radius) - 4px);
  box-shadow: 0 0 0 4px hsl(var(--ring) / 0.1);
}
```

### 6. 트랜지션 표준화

```css
/* tailwind.config.ts에 추가 */
transitionTimingFunction: {
  'smooth': 'cubic-bezier(0.4, 0, 0.2, 1)',
  'bounce': 'cubic-bezier(0.34, 1.56, 0.64, 1)',
}

transitionDuration: {
  'fast': '150ms',
  'normal': '200ms',
  'slow': '300ms',
}
```

---

## 컴포넌트 변경 요약

### Button
- 현재: 양호. 변경 없음.
- 추가: Primary 버튼에 `hover:scale-[1.02] active:scale-[0.98]` 마이크로 모션

### Badge
- 현재: 양호 (CVA 기반).
- 변경: 도트 인디케이터 크기를 `w-1.5 h-1.5`에서 `w-2 h-2`로 미세 증가

### Card
- **상위 호환**: 기존 Card 컴포넌트 유지
- **신규**: `CardElevated` variant 추가 (Double-Bezel)

### Input / Select
- 현재: `h-9` — 모바일에서 터치 영역이 44px 미달 가능
- 변경: `h-10` (40px) + `@media (pointer: coarse) { min-height: 44px }` 유지

### Skeleton
- 현재: `animate-pulse` 단일 패턴
- 변경: `animate-pulse` + shimmer gradient 효과 추가 옵션

---

## 마이그레이션 전략

1. `globals.css` 토큰 값만 변경 → 기존 코드 자동 반영
2. Footer 하드코딩 → CSS 변수로 교체 (1건)
3. 타이포그래피 스케일 → 페이지별 순차 적용
4. Double-Bezel 카드 → 대시보드 메트릭 카드부터 시작
5. 그림자 틴팅 → tailwind.config.ts 확장 후 점진 적용

**원칙: 기존 디자인을 깨지 않고 점진적으로 업그레이드**
