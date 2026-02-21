---
name: verify-frontend-ui
description: 프론트엔드 UI 컴포넌트 품질 검증 (shadcn/ui + Tailwind 기준). UI 컴포넌트 수정 후 사용.
---

## Purpose

1. **디자인 토큰 준수** — shadcn HSL CSS 변수 + Tailwind 시맨틱 클래스 사용, 하드코딩 색상 최소화
2. **접근성(ARIA)** — ARIA 역할, 라벨, 키보드 내비게이션, focus-visible 확인
3. **다크 모드 호환** — `.dark` 클래스 기반 테마 전환 시 색상 정상 동작 확인
4. **TypeScript 타입 안전성** — Props 인터페이스, cva variants, 제네릭 사용 확인
5. **shadcn/ui 패턴 준수** — cn() 유틸리티, cva, forwardRef, Radix 프리미티브 사용 확인

## When to Run

- `frontend/src/components/ui/` 하위 컴포넌트 수정 후
- `frontend/src/app/globals.css` CSS 변수 변경 후
- `frontend/tailwind.config.ts` 테마 변경 후
- 새 UI 컴포넌트 생성 후
- 다크 모드 관련 코드 변경 후

## Related Files

| File | Purpose |
|------|---------|
| `frontend/src/app/globals.css` | HSL CSS 변수 (shadcn + Bio-Rad 시맨틱, light/dark) |
| `frontend/tailwind.config.ts` | Tailwind 테마 (색상 매핑, 키프레임, 폰트) |
| `frontend/src/lib/utils.ts` | cn() 유틸리티 (clsx + tailwind-merge) |
| `frontend/src/components/ui/Badge.tsx` | 상태 뱃지 (cva, variant: info/success/warn/danger/neutral) |
| `frontend/src/components/ui/button.tsx` | shadcn Button (cva, forwardRef, asChild) |
| `frontend/src/components/ui/card.tsx` | shadcn Card (6개 sub-components, forwardRef) |
| `frontend/src/components/ui/input.tsx` | shadcn Input (forwardRef) |
| `frontend/src/components/ui/skeleton.tsx` | shadcn Skeleton (animate-pulse) |
| `frontend/src/components/ui/DataTable.tsx` | 제네릭 테이블 (키보드 내비게이션) |
| `frontend/src/components/ui/Tabs.tsx` | 탭 전환 (ARIA tablist 패턴) |
| `frontend/src/components/ui/Toast.tsx` | 알림 토스트 (cn + variant 매핑) |
| `frontend/src/components/ui/EmptyState.tsx` | 빈 상태 CTA (shadcn Button) |
| `frontend/src/components/ui/FilterBar.tsx` | 필터 검색바 (shadcn Button, role=search) |
| `frontend/src/components/ui/Pagination.tsx` | 페이지 내비게이션 (aria-current) |
| `frontend/src/components/ui/PdfViewer.tsx` | PDF 미리보기 (shadcn Button, SSR 미지원, triggerBlobDownload) |
| `frontend/src/components/ui/PdfExpandModal.tsx` | PDF 확대 모달 (@radix-ui/react-dialog, SSR 미지원, triggerBlobDownload) |
| `frontend/src/components/ui/sonner.tsx` | Sonner Toaster 래퍼 (shadcn toast provider) |
| `frontend/src/components/ui/index.ts` | 배럴 export (PdfViewer 제외) |
| `frontend/src/components/theme-provider.tsx` | next-themes ThemeProvider 래퍼 |
| `frontend/src/components/theme-toggle.tsx` | 다크/라이트 모드 토글 버튼 |
| `frontend/src/app/layout.tsx` | 루트 레이아웃 (sticky footer, header/main/footer 구조) |

## Workflow

### Step 1: Tailwind 시맨틱 클래스 사용 확인

**파일:** `frontend/src/components/ui/*.tsx`

**검사:** 하드코딩 hex 색상이나 rgb() 대신 Tailwind 시맨틱 클래스(text-foreground, bg-card, border-border 등)를 사용하는지 확인.

```bash
grep -rn 'style={{' frontend/src/components/ui/*.tsx | grep -v 'width\|height\|flex\|opacity' | head -10
```

**PASS:** 색상 관련 인라인 스타일 없음 (모두 Tailwind 클래스 사용)
**FAIL:** 하드코딩 색상 인라인 스타일 발견

### Step 2: cn() 유틸리티 사용 확인

**파일:** `frontend/src/components/ui/*.tsx`

**검사:** 조건부 클래스 조합에 cn() 유틸리티를 사용하는지 확인.

```bash
grep -rn "import.*cn.*utils" frontend/src/components/ui/*.tsx
```

**PASS:** cn()을 사용하는 컴포넌트에 import 존재
**FAIL:** 문자열 템플릿으로 클래스 조합 (tailwind-merge 미적용)

### Step 3: ARIA role 속성 확인

**파일:** `frontend/src/components/ui/*.tsx`

**검사:** 인터랙티브 컴포넌트에 적절한 ARIA 역할이 있는지 확인.

```bash
grep -rn "role=" frontend/src/components/ui/*.tsx
```

**필수 매핑:**
- DataTable → `role="table"`
- Tabs → `role="tablist"`, `role="tab"`, `role="tabpanel"`
- Toast → `role="alert"`
- Badge → `role="status"`
- FilterBar → `role="search"`
- Pagination → `role="navigation"`

**PASS:** 모든 필수 role 존재
**FAIL:** role 속성 누락

### Step 4: aria-label 및 접근성 속성 확인

**파일:** `frontend/src/components/ui/*.tsx`

**검사:** 아이콘에 aria-hidden, 버튼에 aria-label, 동적 영역에 aria-live가 있는지 확인.

```bash
grep -rn "aria-hidden\|aria-label\|aria-live\|aria-selected\|aria-current" frontend/src/components/ui/*.tsx
```

**필수 패턴:**
- SVG 아이콘: `aria-hidden="true"`
- 아이콘 전용 버튼: `aria-label="설명"`
- Toast: `aria-live="assertive"` + `aria-atomic="true"`
- Tabs: `aria-selected={boolean}`
- Pagination: `aria-current="page"`
- PdfViewer: 페이지 정보에 `aria-live="polite"`

**PASS:** 모든 필수 ARIA 속성 존재
**FAIL:** 접근성 속성 누락

### Step 5: 키보드 내비게이션 지원 확인

**파일:** `DataTable.tsx`, `Tabs.tsx`, `FilterBar.tsx`

**검사:** 키보드 이벤트 핸들링이 구현되어 있는지 확인.

```bash
grep -rn "onKeyDown\|ArrowLeft\|ArrowRight\|Enter\| Space" frontend/src/components/ui/DataTable.tsx frontend/src/components/ui/Tabs.tsx frontend/src/components/ui/FilterBar.tsx
```

**PASS:** 모든 키보드 이벤트 핸들러 존재
**FAIL:** 키보드 내비게이션 누락

### Step 6: 다크 모드 설정 확인

**파일:** `frontend/tailwind.config.ts`, `frontend/src/app/layout.tsx`

**검사:** darkMode: ["class"] 설정과 ThemeProvider 래핑이 올바른지 확인.

```bash
grep -n 'darkMode\|ThemeProvider\|suppressHydrationWarning' frontend/tailwind.config.ts frontend/src/app/layout.tsx
```

**PASS:** darkMode: ["class"] + ThemeProvider attribute="class" + suppressHydrationWarning
**FAIL:** 다크 모드 설정 불완전

### Step 7: Sticky Footer 레이아웃 확인

**파일:** `frontend/src/app/globals.css`, `frontend/src/app/layout.tsx`

**검사:** body에 `min-h-screen flex flex-col`이 적용되고, main에 `flex-1`이 있어 콘텐츠가 짧아도 footer가 뷰포트 하단에 고정되는지 확인.

```bash
grep -n 'min-h-screen\|flex-col' frontend/src/app/globals.css
grep -n 'flex-1' frontend/src/app/layout.tsx
```

**PASS:** body에 `min-h-screen flex flex-col` + main에 `flex-1` 존재
**FAIL:** sticky footer 레이아웃 클래스 누락 (콘텐츠 짧을 때 footer가 위로 떠오름)

### Step 8: globals.css 최소화 확인

**파일:** `frontend/src/app/globals.css`

**검사:** globals.css가 CSS 변수 + 최소 기반 스타일만 포함하고 컴포넌트 CSS 클래스가 없는지 확인.

```bash
wc -l frontend/src/app/globals.css
grep -c '^\.' frontend/src/app/globals.css
```

**PASS:** 120줄 이하, 컴포넌트 CSS 클래스 없음 (.dark만 허용)
**FAIL:** 레거시 CSS 클래스 잔존 (.btn, .card, .badge 등)

### Step 9: shadcn 컴포넌트 패턴 확인

**파일:** `frontend/src/components/ui/button.tsx`, `card.tsx`, `input.tsx`

**검사:** shadcn 표준 패턴(cva, forwardRef, displayName)을 따르는지 확인.

```bash
grep -rn "forwardRef\|displayName\|cva\|VariantProps" frontend/src/components/ui/button.tsx frontend/src/components/ui/card.tsx frontend/src/components/ui/input.tsx
```

**PASS:** forwardRef + displayName + cva (button) 존재
**FAIL:** shadcn 패턴 불완전

### Step 10: PdfViewer SSR 배럴 export 제한 확인

**파일:** `frontend/src/components/ui/index.ts`

**검사:** PdfViewer와 PdfExpandModal이 배럴 export에서 제외되고 사용처에서 dynamic import + ssr: false 사용.

```bash
grep -n "PdfViewer\|PdfExpandModal" frontend/src/components/ui/index.ts
grep -rn "dynamic.*PdfViewer\|dynamic.*PdfExpandModal\|ssr.*false" frontend/src/components/inquiry/*.tsx frontend/src/components/ui/PdfViewer.tsx
```

**PASS:** index.ts에 PdfViewer/PdfExpandModal export 없음 + 사용처에서 dynamic + ssr: false
**FAIL:** 배럴 export에 포함 또는 직접 import

### Step 11: 배럴 export 완전성 확인

**파일:** `frontend/src/components/ui/index.ts`

**검사:** 모든 UI 컴포넌트가 배럴 export에 포함되는지 확인 (PdfViewer 제외).

```bash
grep -c "export" frontend/src/components/ui/index.ts
```

**PASS:** Badge, DataTable, Pagination, Tabs, Toast, EmptyState, FilterBar, Toaster, Button, Card*, Input, Skeleton 모두 export (PdfViewer, PdfExpandModal 제외)
**FAIL:** 신규 컴포넌트 export 누락 (단, SSR 미지원 컴포넌트는 제외가 정상)

## Output Format

| # | 검사 항목 | 결과 | 상세 |
|---|----------|------|------|
| 1 | Tailwind 시맨틱 클래스 | PASS/FAIL | 하드코딩 색상 스타일 |
| 2 | cn() 유틸리티 사용 | PASS/FAIL | 미사용 컴포넌트 |
| 3 | ARIA role 속성 | PASS/FAIL | 누락 role |
| 4 | aria-label/hidden/live | PASS/FAIL | 누락 속성 |
| 5 | 키보드 내비게이션 | PASS/FAIL | 미구현 핸들러 |
| 6 | 다크 모드 설정 | PASS/FAIL | 설정 불완전 항목 |
| 7 | Sticky Footer 레이아웃 | PASS/FAIL | body min-h-screen + main flex-1 |
| 8 | globals.css 최소화 | PASS/FAIL | 라인 수, 레거시 클래스 |
| 9 | shadcn 컴포넌트 패턴 | PASS/FAIL | 누락 패턴 |
| 10 | PdfViewer SSR 제한 | PASS/FAIL | import 방식 |
| 11 | 배럴 export 완전성 | PASS/FAIL | 누락 export |

## Exceptions

다음은 **위반이 아닙니다**:

1. **동적 계산 값** — `style={{ width: \`${percentage}%\` }}` 등 런타임 계산이 필요한 인라인 스타일은 허용
2. **SVG 내부 색상** — SVG fill/stroke가 currentColor를 사용하면 허용
3. **Badge.tsx dotColors** — SVG fill에 사용되는 hex 색상 객체는 Tailwind 클래스로 대체 불가하므로 허용
4. **cva variant 내 Tailwind 색상** — Badge의 bg-blue-50, text-sky-700 등 variant별 하드코딩 색상은 dark: 변형이 있으면 허용
