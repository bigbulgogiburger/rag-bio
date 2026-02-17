---
name: verify-frontend-ui
description: 프론트엔드 UI 컴포넌트 품질 검증. UI 컴포넌트 수정 후 사용.
---

## Purpose

1. **CSS 변수 준수** — 하드코딩된 색상/크기 대신 globals.css 디자인 토큰 사용 확인
2. **접근성(ARIA)** — 적절한 ARIA 역할, 라벨, 키보드 내비게이션 확인
3. **반응형 디자인** — 1279px(태블릿), 767px(모바일) 브레이크포인트 대응 확인
4. **TypeScript 타입 안전성** — Props 인터페이스, union 타입, 제네릭 사용 확인
5. **디자인 토큰 일관성** — 새 컴포넌트가 기존 토큰 체계를 따르는지 확인

## When to Run

- `frontend/src/components/ui/` 하위 컴포넌트 수정 후
- `frontend/src/app/globals.css` 디자인 토큰 변경 후
- 새 UI 컴포넌트 생성 후
- 인라인 스타일 추가/수정 후
- ARIA 속성 관련 코드 변경 후

## Related Files

| File | Purpose |
|------|---------|
| `frontend/src/app/globals.css` | CSS 변수 (디자인 토큰) 정의 |
| `frontend/src/components/ui/Badge.tsx` | 상태 뱃지 (variant: info/success/warn/danger/neutral) |
| `frontend/src/components/ui/DataTable.tsx` | 제네릭 테이블 (키보드 내비게이션) |
| `frontend/src/components/ui/Tabs.tsx` | 탭 전환 (ARIA tablist 패턴) |
| `frontend/src/components/ui/Toast.tsx` | 알림 토스트 (role=alert) |
| `frontend/src/components/ui/EmptyState.tsx` | 빈 상태 CTA |
| `frontend/src/components/ui/FilterBar.tsx` | 필터 검색바 (role=search) |
| `frontend/src/components/ui/Pagination.tsx` | 페이지 내비게이션 (aria-current) |

## Workflow

### Step 1: 하드코딩 색상 검출

**파일:** `frontend/src/components/ui/*.tsx`

**검사:** TSX 파일에서 #hex 색상 또는 rgb() 값이 인라인으로 사용되는지 확인.

```bash
grep -rn "#[0-9a-fA-F]\{3,8\}\|rgb(" frontend/src/components/ui/*.tsx
```

**PASS:** hex/rgb 색상 없음 (모두 CSS 변수 또는 클래스 사용)
**FAIL:** 하드코딩 색상 발견 — `var(--color-*)` 토큰으로 대체 필요

### Step 2: CSS 변수 사용 확인 (globals.css)

**파일:** `frontend/src/app/globals.css`

**검사:** 컴포넌트 클래스에서 hex 색상 대신 CSS 변수를 사용하는지 확인.

```bash
grep -n "background:\|color:\|border:" frontend/src/app/globals.css | grep "#[0-9a-fA-F]" | head -20
```

**PASS:** 모든 스타일이 `var(--color-*)` 사용
**FAIL:** 하드코딩 hex 값 존재 — CSS 변수로 교체 필요

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

### Step 4: aria-label 및 aria-hidden 확인

**파일:** `frontend/src/components/ui/*.tsx`

**검사:** 아이콘에 aria-hidden, 버튼에 aria-label이 있는지 확인.

```bash
grep -rn "aria-hidden\|aria-label\|aria-selected\|aria-current\|aria-live" frontend/src/components/ui/*.tsx
```

**필수 패턴:**
- SVG 아이콘: `aria-hidden="true"`
- 아이콘 전용 버튼: `aria-label="설명"`
- Toast: `aria-live="assertive"` + `aria-atomic="true"`
- Tabs: `aria-selected={boolean}`
- Pagination: `aria-current="page"`

**PASS:** 모든 필수 ARIA 속성 존재
**FAIL:** 접근성 속성 누락

### Step 5: 키보드 내비게이션 지원 확인

**파일:** `DataTable.tsx`, `Tabs.tsx`, `FilterBar.tsx`

**검사:** 키보드 이벤트 핸들링이 구현되어 있는지 확인.

```bash
grep -rn "onKeyDown\|ArrowLeft\|ArrowRight\|Enter\| Space" frontend/src/components/ui/DataTable.tsx frontend/src/components/ui/Tabs.tsx frontend/src/components/ui/FilterBar.tsx
```

**필수 패턴:**
- DataTable: Enter/Space로 행 클릭
- Tabs: ArrowLeft/Right로 탭 이동, Home/End로 처음/마지막
- FilterBar: Enter로 검색 실행

**PASS:** 모든 키보드 이벤트 핸들러 존재
**FAIL:** 키보드 내비게이션 누락

### Step 6: 반응형 브레이크포인트 확인

**파일:** `frontend/src/app/globals.css`

**검사:** 1279px(태블릿)과 767px(모바일) 미디어 쿼리가 존재하는지 확인.

```bash
grep -n "@media.*1279\|@media.*767" frontend/src/app/globals.css
```

**PASS:** 두 브레이크포인트 모두 존재하고 레이아웃 조정 포함
**FAIL:** 브레이크포인트 누락 또는 불완전한 반응형 처리

### Step 7: TypeScript Props 인터페이스 확인

**파일:** `frontend/src/components/ui/*.tsx`

**검사:** 모든 컴포넌트가 Props 인터페이스를 명시적으로 정의하는지 확인.

```bash
grep -rn "interface.*Props\|type.*Props" frontend/src/components/ui/*.tsx
```

**PASS:** 모든 컴포넌트에 Props 인터페이스 존재
**FAIL:** Props 타입 없이 any 또는 암시적 타입 사용

### Step 8: 인라인 스타일 최소화 확인

**파일:** `frontend/src/components/ui/*.tsx`

**검사:** 인라인 style 객체 사용을 확인하고 CSS 클래스로 대체 가능한지 평가.

```bash
grep -rn "style={{" frontend/src/components/ui/*.tsx | wc -l
```

**PASS:** 인라인 스타일 5개 미만
**FAIL:** 과도한 인라인 스타일 — CSS 클래스 추출 권장

## Output Format

| # | 검사 항목 | 결과 | 상세 |
|---|----------|------|------|
| 1 | 하드코딩 색상 (TSX) | PASS/FAIL | 발견 위치 목록 |
| 2 | CSS 변수 사용 | PASS/FAIL | 하드코딩 hex 목록 |
| 3 | ARIA role 속성 | PASS/FAIL | 누락 role 목록 |
| 4 | aria-label/hidden | PASS/FAIL | 누락 속성 목록 |
| 5 | 키보드 내비게이션 | PASS/FAIL | 미구현 핸들러 |
| 6 | 반응형 브레이크포인트 | PASS/FAIL | 누락 미디어쿼리 |
| 7 | TypeScript Props | PASS/FAIL | 미정의 컴포넌트 |
| 8 | 인라인 스타일 최소화 | PASS/FAIL | 인라인 스타일 수 |

## Exceptions

다음은 **위반이 아닙니다**:

1. **동적 계산 값** — `style={{ width: \`${percentage}%\` }}` 등 런타임 계산이 필요한 인라인 스타일은 허용
2. **SVG 내부 색상** — SVG 아이콘의 fill/stroke가 currentColor를 사용하면 허용 (CSS 상속)
3. **서드파티 라이브러리 스타일** — 외부 컴포넌트 래핑 시 불가피한 인라인 스타일은 허용
