# UI QA 리포트 - Sprint 9 UI/UX 현대화

## 요약
- 검증 항목: 25개
- Pass: 21개
- Issue: 4개

---

## 1. 디자인 토큰

| 네임스페이스 | 예상 | 실제 | 결과 |
|------------|------|------|------|
| `--color-*` | 18개 | 18개 | Pass |
| `--space-*` | 6개 (xs,sm,md,lg,xl,2xl) | 6개 | Pass |
| `--font-*` (family, size, weight) | 10개 | 10개 | Pass |
| `--line-height-*` | 2개 (tight, normal) | 2개 | Pass |
| `--radius-*` | 4개 (sm,md,lg,full) | 4개 | Pass |
| `--shadow-*` | 3개 (sm, card, modal) | 3개 | Pass |
| `--transition-*` | 2개 (fast, normal) | 2개 | Pass |

### 토큰 값 검증

| 토큰 | 예상 값 | 실제 값 | 결과 |
|------|--------|--------|------|
| `--space-xs` | 4px | 4px | Pass |
| `--space-sm` | 8px | 8px | Pass |
| `--space-md` | 16px | 16px | Pass |
| `--space-lg` | 24px | 24px | Pass |
| `--space-xl` | 32px | 32px | Pass |
| `--space-2xl` | 48px | 48px | Pass |
| `--radius-sm` | 8px | 8px | Pass |
| `--radius-md` | 12px | 12px | Pass |
| `--radius-lg` | 16px | 16px | Pass |
| `--radius-full` | 9999px | 9999px | Pass |
| `--transition-fast` | 150ms | 150ms ease | Pass |
| `--transition-normal` | 200ms | 200ms ease | Pass |

### CSS 클래스 토큰 참조 변환

| 클래스 | 토큰 참조 여부 | 결과 |
|--------|--------------|------|
| `.card` | `var(--color-card)`, `var(--color-line)`, `var(--radius-lg)`, `var(--space-lg)`, `var(--shadow-card)` | Pass |
| `.btn` | `var(--color-line)`, `var(--radius-sm)`, `var(--color-card)`, `var(--color-text)`, `var(--font-weight-bold)`, `var(--transition-fast)` | Pass |
| `.badge` (base) | `var(--radius-full)`, `var(--font-size-xs)`, `var(--font-weight-bold)` | Pass |
| `.badge-info` | 하드코딩 `#eaf2ff`, `#1d4ed8` | **Issue** |
| `.badge-success` | 하드코딩 `#e8f8ee`, `#15803d` | **Issue** |
| `.badge-warn` | `var(--color-warn-light)`, 하드코딩 `#b45309` | Partial |
| `.badge-danger` | `var(--color-danger-light)`, 하드코딩 `#b91c1c` | Partial |

---

## 2. UI 컴포넌트 (7개)

### 2.1 Badge.tsx
- **Props 타입**: `variant: 'info' | 'success' | 'warn' | 'danger' | 'neutral'`, `children: React.ReactNode`, `style?: React.CSSProperties` -- Pass
- **ARIA**: `role="status"` -- Pass. `aria-label` 미존재 -- **Issue** (스펙 요구사항에 aria-label이 있으나 구현 미적용)
- **토큰 사용**: CSS 클래스 `badge badge-${variant}` 참조, 인라인 하드코딩 없음 -- Pass
- **로직**: neutral variant는 기본 `badge` 클래스만 적용 -- 올바름

### 2.2 DataTable.tsx
- **Props 타입**: 제네릭 `Column<T>` (key, header, render?, width?), `DataTableProps<T>` (columns, data, onRowClick?, emptyMessage?) -- Pass
- **ARIA**: `role="table"` -- Pass
- **키보드**: Enter/Space key 핸들러, `tabIndex={0}` (onRowClick 시) -- Pass
- **토큰 사용**: CSS 클래스 `data-table`, `empty-state` 참조 -- Pass
- **Empty 처리**: `emptyMessage` 기본값 `'데이터가 없습니다'` -- Pass

### 2.3 Pagination.tsx
- **Props 타입**: page, totalPages, totalElements, size, onPageChange, onSizeChange, sizeOptions? -- Pass
- **페이지 버튼 로직**: 최대 5개 (maxVisible=5), startPage/endPage 계산 -- Pass
- **Prev/Next disabled**: `page === 0` / `page >= totalPages - 1` -- Pass
- **Size selector**: sizeOptions 기본값 `[20, 50, 100]`, select 요소 -- Pass
- **ARIA**: `role="navigation"`, `aria-label="페이지네이션"`, `aria-current="page"`, `aria-label` on prev/next/page buttons -- Pass

### 2.4 Tabs.tsx
- **Props 타입**: `Tab` (key, label, content), `TabsProps` (tabs, defaultTab?) -- Pass
- **ARIA**: `role="tablist"`, `role="tab"`, `aria-selected`, `aria-controls`, `role="tabpanel"`, `aria-labelledby` -- Pass
- **키보드**: ArrowLeft/ArrowRight (순환), Enter/Space -- Pass
- **tabIndex**: 활성 탭 `0`, 비활성 탭 `-1` (roving tabindex 패턴) -- Pass
- **'use client'**: 클라이언트 컴포넌트 선언 -- Pass

### 2.5 Toast.tsx
- **Props 타입**: message, variant ('success'|'error'|'warn'|'info'), onClose, duration? (기본값 3000) -- Pass
- **Auto-dismiss**: `setTimeout(onClose, duration)` + `clearTimeout` cleanup -- Pass
- **ARIA**: `role="alert"`, `aria-live="assertive"`, `aria-atomic="true"` -- Pass
- **닫기 버튼**: `aria-label="닫기"` -- Pass
- **토큰 사용**: 닫기 버튼에 인라인 스타일 사용 (marginLeft, background, border 등) -- 경미한 이슈 (기능에 문제 없음)

### 2.6 EmptyState.tsx
- **Props 타입**: title, description?, action? ({label, onClick}) -- Pass
- **CTA 버튼**: action 존재 시 `btn btn-primary` 클래스 렌더링 -- Pass
- **토큰 사용**: CSS 클래스 `empty-state`, `btn btn-primary` 참조 -- Pass

### 2.7 FilterBar.tsx
- **Props 타입**: `FilterField` (key, label, type: 'select'|'text'|'date', options?, placeholder?), `FilterBarProps` (fields, values, onChange, onSearch) -- Pass
- **Enter key search**: text/date 입력에서 `onKeyDown` + `e.key === 'Enter'` -> `onSearch()` -- Pass
- **ARIA**: `aria-label={field.label}` on select/input -- Pass
- **토큰 사용**: label 스타일에 `var(--font-size-sm)`, `var(--font-weight-medium)` (인라인 스타일이지만 토큰 참조) -- Pass

---

## 3. 반응형

### 태블릿 (max-width: 1279px)
| 규칙 | 예상 | 실제 | 결과 |
|------|------|------|------|
| metrics-grid | 2열 | `grid-template-columns: 1fr 1fr` | Pass |
| filter-bar | 세로 배치 | `flex-direction: column; align-items: stretch` | Pass |
| filter-bar .label | 전체 너비 | `min-width: 100%` | Pass |
| filter-bar .btn | 전체 너비 | `width: 100%` | Pass |

### 모바일 (max-width: 767px)
| 규칙 | 예상 | 실제 | 결과 |
|------|------|------|------|
| metrics-grid | 1열 | `grid-template-columns: 1fr` | Pass |
| data-table | overflow-x auto | `display: block; overflow-x: auto` | Pass |
| tabs-header | overflow-x auto | `overflow-x: auto` | Pass |
| topbar-inner | 세로 배치 | `flex-direction: column` | Pass |
| pagination | 세로 배치, 중앙정렬 | `flex-direction: column; align-items: stretch; text-align: center` | Pass |

---

## 4. 접근성

| 항목 | 예상 | 실제 | 결과 |
|------|------|------|------|
| `*:focus-visible` outline | 존재 | `outline: 2px solid var(--color-primary); outline-offset: 2px` (line 80-83) | Pass |
| Tabs `role="tablist"` | 존재 | `<div role="tablist" aria-label="탭 목록">` | Pass |
| Tabs `role="tab"` | 존재 | `<button role="tab">` | Pass |
| Tabs `aria-selected` | 존재 | `aria-selected={activeTab === tab.key}` | Pass |
| Tabs ArrowLeft/Right | 존재 | 순환 네비게이션 구현됨 | Pass |
| DataTable `role="table"` | 존재 | `<table role="table">` | Pass |
| DataTable Enter key | 존재 | `Enter` + `Space` key 핸들러 | Pass |
| Toast `role="alert"` | 존재 | `role="alert"` | Pass |
| Toast `aria-live` | "assertive" | `aria-live="assertive"` | Pass |
| Badge `role="status"` | 존재 | `role="status"` | Pass |
| Badge `aria-label` | 존재 | **미구현** | **Issue** |

---

## 5. Barrel Export

파일: `frontend/src/components/ui/index.ts`

| 컴포넌트 | Export 여부 | 결과 |
|----------|-----------|------|
| Badge | `export { default as Badge } from './Badge'` | Pass |
| DataTable | `export { default as DataTable } from './DataTable'` | Pass |
| Pagination | `export { default as Pagination } from './Pagination'` | Pass |
| Tabs | `export { default as Tabs } from './Tabs'` | Pass |
| Toast | `export { default as Toast } from './Toast'` | Pass |
| EmptyState | `export { default as EmptyState } from './EmptyState'` | Pass |
| FilterBar | `export { default as FilterBar } from './FilterBar'` | Pass |

7/7 컴포넌트 모두 barrel export됨 -- Pass

---

## 주요 발견 사항

### Issues (4건)

1. **[MEDIUM] Badge.tsx - aria-label 미구현**
   - 스펙에서 `aria-label`이 요구되나 Badge 컴포넌트에 aria-label prop 및 속성이 없음
   - `role="status"`는 존재하나 스크린 리더에서 badge의 의미를 전달하려면 aria-label 추가 권장
   - **권장**: BadgeProps에 `ariaLabel?: string` 추가 후 `<span aria-label={ariaLabel}>` 적용

2. **[LOW] badge-info, badge-success CSS - 하드코딩된 색상 값**
   - `.badge-info`의 `background: #eaf2ff`, `color: #1d4ed8` 등이 디자인 토큰(`--color-info-light`, `--color-info`) 대신 하드코딩됨
   - `.badge-success`도 동일 (`#e8f8ee`, `#15803d` 대신 토큰 사용 필요)
   - `.badge-warn`과 `.badge-danger`는 background에 토큰 사용하나 color 값은 하드코딩
   - **권장**: 모든 badge variant에서 `var(--color-*-light)` / `var(--color-*)` 토큰 사용으로 통일

3. **[LOW] Toast.tsx 닫기 버튼 - 인라인 스타일**
   - 닫기 버튼의 marginLeft, background, border, cursor, fontSize, lineHeight, color, opacity가 인라인 스타일로 정의됨
   - 기능적 문제는 없으나 디자인 토큰 일관성 측면에서 CSS 클래스로 분리 권장

4. **[INFO] 레거시 변수 잔존**
   - `globals.css` 상단에 `--bg`, `--bg-soft`, `--card`, `--text`, `--muted`, `--line`, `--primary`, `--primary-strong`, `--success`, `--warn`, `--danger`, `--radius`, `--shadow` 등 레거시 변수가 "하위 호환" 주석과 함께 존재
   - 모든 컴포넌트가 새 토큰을 참조하고 있으므로, 마이그레이션 완료 후 제거 가능

### 개선 권장 사항

1. Badge에 `aria-label` prop 추가 (접근성 향상)
2. `.badge-info`, `.badge-success` CSS에서 하드코딩 색상을 디자인 토큰으로 교체
3. 레거시 CSS 변수는 기존 페이지 마이그레이션 완료 확인 후 제거 계획 수립
4. Toast 닫기 버튼 스타일을 CSS 클래스로 분리하여 유지보수성 향상
