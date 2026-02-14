# 33. Sprint 9 실행백로그 — UI/UX 현대화

> 상태: **완료 (UI 컴포넌트 분해/적용 + 빌드 검증 완료)**

## 목표

1. **공통 UI 컴포넌트** 추출 → 디자인 시스템 기반 재사용 체계 구축
2. `inquiry-form.tsx` (500+ LOC 단일 파일) → **탭별 컴포넌트 분해**
3. `/inquiries/{id}` 상세 페이지 **4개 탭 완성** (분석·답변·이력)
4. 대시보드 리디자인 + **반응형 레이아웃** 적용
5. 접근성(a11y) 개선

---

## 운영 원칙

1. **기능 변경 없음**: API 호출 로직과 비즈니스 플로우는 그대로 유지, UI 구조만 재배치
2. **점진적 분해**: inquiry-form.tsx의 코드를 그대로 새 컴포넌트로 이동 (로직 변경 최소화)
3. **디자인 토큰 우선**: 인라인 스타일 제거, CSS 변수 기반으로 통일
4. **회귀 방지**: 분해 단위마다 기존 기능 스모크 테스트

---

## FE-01. 디자인 토큰 정리 (P0)

### 목적

현재 `globals.css`의 CSS 변수를 **체계적인 디자인 토큰**으로 확장하고, 모든 컴포넌트가 이 토큰만 참조하도록 한다.

### 작업 내용

**수정 파일:** `frontend/src/app/globals.css`

#### 현재 상태

```css
:root {
  --bg: #f3f6fb;
  --card: #ffffff;
  --text: #0f172a;
  --muted: #64748b;
  --primary: #2563eb;
  --success: #16a34a;
  --warn: #d97706;
  --danger: #dc2626;
  --radius: 14px;
}
```

#### 확장 후

```css
:root {
  /* ===== 색상 ===== */
  --color-bg:           #f3f6fb;
  --color-bg-soft:      #eef3ff;
  --color-card:         #ffffff;
  --color-text:         #0f172a;
  --color-text-secondary: #475569;
  --color-muted:        #64748b;
  --color-line:         #dbe2ea;
  --color-primary:      #2563eb;
  --color-primary-hover: #1d4ed8;
  --color-primary-light: #dbeafe;
  --color-success:      #16a34a;
  --color-success-light: #dcfce7;
  --color-warn:         #d97706;
  --color-warn-light:   #fef3c7;
  --color-danger:       #dc2626;
  --color-danger-light: #fee2e2;
  --color-info:         #0284c7;
  --color-info-light:   #e0f2fe;

  /* ===== 간격 ===== */
  --space-xs:  4px;
  --space-sm:  8px;
  --space-md:  16px;
  --space-lg:  24px;
  --space-xl:  32px;
  --space-2xl: 48px;

  /* ===== 타이포그래피 ===== */
  --font-family: 'Pretendard', 'Noto Sans KR', -apple-system, sans-serif;
  --font-size-xs:   0.75rem;    /* 12px */
  --font-size-sm:   0.8125rem;  /* 13px */
  --font-size-base: 0.9375rem;  /* 15px */
  --font-size-lg:   1.125rem;   /* 18px */
  --font-size-xl:   1.25rem;    /* 20px */
  --font-size-2xl:  1.5rem;     /* 24px */
  --font-weight-normal: 400;
  --font-weight-medium: 500;
  --font-weight-bold: 600;
  --line-height-tight: 1.3;
  --line-height-normal: 1.6;

  /* ===== 라운드 ===== */
  --radius-sm:  8px;
  --radius-md:  12px;
  --radius-lg:  16px;
  --radius-full: 9999px;

  /* ===== 그림자 ===== */
  --shadow-sm:    0 1px 2px rgba(0,0,0,0.04);
  --shadow-card:  0 1px 3px rgba(0,0,0,0.06), 0 1px 2px rgba(0,0,0,0.04);
  --shadow-modal: 0 4px 24px rgba(0,0,0,0.12);

  /* ===== 전환 ===== */
  --transition-fast: 150ms ease;
  --transition-normal: 200ms ease;
}
```

#### 기존 클래스 마이그레이션

기존 `globals.css`의 `.card`, `.btn`, `.badge-*` 등의 클래스에서 하드코딩된 값을 토큰 참조로 변경:

```css
/* AS-IS */
.card {
  background: #fff;
  border: 1px solid #dbe2ea;
  border-radius: 14px;
  padding: 1.5rem 1.6rem;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
}

/* TO-BE */
.card {
  background: var(--color-card);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-lg);
  padding: var(--space-lg) var(--space-lg);
  box-shadow: var(--shadow-card);
}
```

### 수용 기준

- [ ] 모든 CSS 변수가 `--color-*`, `--space-*`, `--font-*`, `--radius-*`, `--shadow-*` 네임스페이스로 정리됨
- [ ] 기존 `.card`, `.btn`, `.badge-*` 클래스가 토큰 참조로 변경됨
- [ ] 화면 렌더링 결과가 기존과 시각적으로 동일 (회귀 없음)

---

## FE-02. 공통 UI 컴포넌트 추출 (P0)

### 목적

반복 사용되는 UI 패턴을 독립 컴포넌트로 추출하여 재사용한다.

### 작업 내용

**신규 디렉토리:** `frontend/src/components/ui/`

#### 1. Badge 컴포넌트

**신규 파일:** `frontend/src/components/ui/Badge.tsx`

```typescript
interface BadgeProps {
  variant: 'info' | 'success' | 'warn' | 'danger' | 'neutral';
  children: React.ReactNode;
}

// variant별 CSS 클래스 매핑
// 사용 예: <Badge variant="success">인덱싱 완료</Badge>
```

**적용 대상:**
- 문서 상태 배지 (inquiry-form, knowledge-base, inquiry 상세)
- 답변 상태 배지 (DRAFT~SENT)
- 판정 배지 (SUPPORTED~CONDITIONAL)

#### 2. DataTable 컴포넌트

**신규 파일:** `frontend/src/components/ui/DataTable.tsx`

```typescript
interface Column<T> {
  key: string;
  header: string;
  render?: (item: T) => React.ReactNode;  // 커스텀 렌더러
  width?: string;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  data: T[];
  onRowClick?: (item: T) => void;
  emptyMessage?: string;              // 데이터 없을 때 안내 문구
}

// 사용 예:
// <DataTable
//   columns={[
//     { key: 'question', header: '질문 요약' },
//     { key: 'status', header: '상태', render: (item) => <Badge ...>{labelInquiryStatus(item.status)}</Badge> },
//   ]}
//   data={inquiries}
//   onRowClick={(item) => router.push(`/inquiries/${item.inquiryId}`)}
//   emptyMessage="등록된 문의가 없습니다"
// />
```

**적용 대상:**
- `/inquiries` 목록 페이지
- `/knowledge-base` 문서 목록
- 문의 상세의 문서 목록

#### 3. Pagination 컴포넌트

**신규 파일:** `frontend/src/components/ui/Pagination.tsx`

```typescript
interface PaginationProps {
  page: number;
  totalPages: number;
  totalElements: number;
  size: number;
  onPageChange: (page: number) => void;
  onSizeChange: (size: number) => void;
  sizeOptions?: number[];              // 기본: [20, 50, 100]
}

// 렌더링:
// "전체 142건 중 1-20건"
// [◀ 이전] [1] [2] [3] ... [8] [다음 ▶]   [20건 ▼]
```

**적용 대상:**
- `/inquiries` 목록
- `/knowledge-base` 문서 목록
- 감사 로그 목록

#### 4. Tabs 컴포넌트

**신규 파일:** `frontend/src/components/ui/Tabs.tsx`

```typescript
interface Tab {
  key: string;
  label: string;
  content: React.ReactNode;
}

interface TabsProps {
  tabs: Tab[];
  defaultTab?: string;
}

// 렌더링:
// ┌──────┬──────┬──────┬──────┐
// │ 탭1  │ 탭2  │ 탭3  │ 탭4  │
// ├──────┴──────┴──────┴──────┤
// │  [활성 탭 내용]            │
// └───────────────────────────┘
```

**적용 대상:**
- `/inquiries/{id}` 상세 페이지

#### 5. Toast 컴포넌트

**신규 파일:** `frontend/src/components/ui/Toast.tsx`

```typescript
interface ToastProps {
  message: string;
  variant: 'success' | 'error' | 'warn' | 'info';
  onClose: () => void;
  duration?: number;  // 자동 닫힘 (ms), 기본 3000
}

// 화면 우측 상단에 표시, duration 후 자동 사라짐
```

**적용 대상:**
- 문의 등록 성공/실패
- 인덱싱 실행 결과
- 리뷰/승인/발송 결과
- KB 문서 등록/삭제 결과

#### 6. EmptyState 컴포넌트

**신규 파일:** `frontend/src/components/ui/EmptyState.tsx`

```typescript
interface EmptyStateProps {
  title: string;
  description?: string;
  action?: { label: string; onClick: () => void; };
}

// 사용 예:
// <EmptyState
//   title="등록된 문의가 없습니다"
//   description="새 문의를 작성하여 CS 대응을 시작하세요."
//   action={{ label: "문의 작성", onClick: () => router.push('/inquiries/new') }}
// />
```

#### 7. FilterBar 컴포넌트

**신규 파일:** `frontend/src/components/ui/FilterBar.tsx`

```typescript
interface FilterField {
  key: string;
  label: string;
  type: 'select' | 'text' | 'date';
  options?: { value: string; label: string; }[];  // select용
  placeholder?: string;
}

interface FilterBarProps {
  fields: FilterField[];
  values: Record<string, string>;
  onChange: (key: string, value: string) => void;
  onSearch: () => void;
}

// 렌더링:
// [필터1 ▼] [필터2 ▼] [날짜 📅] [검색어____]  [검색]
```

**적용 대상:**
- `/inquiries` 목록 필터
- `/knowledge-base` 문서 필터

### CSS 추가

**수정 파일:** `frontend/src/app/globals.css`

```css
/* ===== DataTable ===== */
.data-table { width: 100%; border-collapse: collapse; }
.data-table th {
  text-align: left;
  padding: var(--space-sm) var(--space-md);
  font-weight: var(--font-weight-medium);
  font-size: var(--font-size-sm);
  color: var(--color-muted);
  border-bottom: 1px solid var(--color-line);
}
.data-table td {
  padding: var(--space-sm) var(--space-md);
  font-size: var(--font-size-base);
  border-bottom: 1px solid var(--color-line);
}
.data-table tr:hover { background: var(--color-bg-soft); cursor: pointer; }

/* ===== Tabs ===== */
.tabs-header {
  display: flex; gap: var(--space-xs);
  border-bottom: 2px solid var(--color-line);
  margin-bottom: var(--space-lg);
}
.tab-button {
  padding: var(--space-sm) var(--space-lg);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  color: var(--color-muted);
  border: none; background: none;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  cursor: pointer;
  transition: var(--transition-fast);
}
.tab-button.active {
  color: var(--color-primary);
  border-bottom-color: var(--color-primary);
}

/* ===== Toast ===== */
.toast {
  position: fixed; top: var(--space-lg); right: var(--space-lg);
  padding: var(--space-md) var(--space-lg);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-modal);
  z-index: 1000;
  animation: slideIn var(--transition-normal);
}
.toast-success { background: var(--color-success-light); color: var(--color-success); }
.toast-error   { background: var(--color-danger-light);  color: var(--color-danger); }
.toast-warn    { background: var(--color-warn-light);    color: var(--color-warn); }
.toast-info    { background: var(--color-info-light);    color: var(--color-info); }

@keyframes slideIn {
  from { transform: translateX(100%); opacity: 0; }
  to   { transform: translateX(0);    opacity: 1; }
}

/* ===== Pagination ===== */
.pagination {
  display: flex; align-items: center; justify-content: space-between;
  padding: var(--space-md) 0;
  font-size: var(--font-size-sm);
  color: var(--color-muted);
}
.pagination-buttons { display: flex; gap: var(--space-xs); }
.pagination-btn {
  padding: var(--space-xs) var(--space-sm);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-sm);
  background: var(--color-card);
  cursor: pointer;
  transition: var(--transition-fast);
}
.pagination-btn.active {
  background: var(--color-primary);
  color: white;
  border-color: var(--color-primary);
}
.pagination-btn:disabled { opacity: 0.4; cursor: not-allowed; }

/* ===== EmptyState ===== */
.empty-state {
  text-align: center;
  padding: var(--space-2xl) var(--space-lg);
  color: var(--color-muted);
}
.empty-state h3 { font-size: var(--font-size-lg); margin-bottom: var(--space-sm); }
.empty-state p { margin-bottom: var(--space-lg); }

/* ===== FilterBar ===== */
.filter-bar {
  display: flex; gap: var(--space-sm);
  align-items: center; flex-wrap: wrap;
  padding: var(--space-md) 0;
}
```

### 수용 기준

- [ ] 7개 공통 컴포넌트가 `src/components/ui/`에 생성됨
- [ ] 각 컴포넌트가 독립적으로 사용 가능 (props만으로 동작)
- [ ] 기존 `.card`, `.btn`, `.badge-*` 등의 클래스와 시각적으로 일관됨

---

## FE-03. inquiry-form.tsx 분해 (P0)

### 목적

현재 `inquiry-form.tsx` (500+ LOC)에 밀집된 워크플로우를 **4개의 탭 컴포넌트**로 분해하여 `/inquiries/{id}` 상세 페이지에서 사용한다.

### 분해 구조

```
현재 inquiry-form.tsx 내용:
├── [섹션 1] 문의 등록           → /inquiries/new/page.tsx (Sprint 7에서 이미 이관)
├── [섹션 2] 문의 조회 + 인덱싱   → InquiryInfoTab.tsx
├── [섹션 3] 분석 (근거 검색+판정) → InquiryAnalysisTab.tsx
├── [섹션 3] 초안 생성+리뷰+승인  → InquiryAnswerTab.tsx
└── [섹션 3] 버전 이력           → InquiryHistoryTab.tsx
```

### 신규 파일 목록

#### 1. `frontend/src/components/inquiry/InquiryInfoTab.tsx`

**이관 대상 (inquiry-form.tsx에서):**
- 문의 ID 입력 + 조회 버튼
- 문의 정보 카드 (질문, 채널, 상태, 접수일)
- 인덱싱 상태 요약 (전체/업로드/파싱중/파싱완료/청크완료/인덱싱완료/실패)
- 문서 목록 테이블 (파일명, 상태 배지, 파일크기, OCR 신뢰도, 청크수, 벡터수, 에러)
- 인덱싱 실행 / 실패 건 재처리 버튼

```typescript
interface InquiryInfoTabProps {
  inquiryId: string;
}

// 내부에서 getInquiry(), listInquiryDocuments(), getInquiryIndexingStatus(), runInquiryIndexing() 호출
// DataTable 컴포넌트 사용
// Badge 컴포넌트로 문서 상태 표시
// labels.ts의 labelDocStatus() 사용
```

#### 2. `frontend/src/components/inquiry/InquiryAnalysisTab.tsx`

**이관 대상:**
- 분석 질문 입력 textarea
- "근거 검색 + 판정" 버튼
- 분석 결과 표시:
  - 판정 (labelVerdict)
  - 신뢰도 (confidence)
  - 사유 (reason)
  - 리스크 플래그 (labelRiskFlag)
  - 근거 목록 (chunkId, score, excerpt, sourceType 배지)

```typescript
interface InquiryAnalysisTabProps {
  inquiryId: string;
}

// 내부에서 analyzeInquiry() 호출
// 출처 배지: [지식 기반] / [문의 첨부] (Sprint 8 FE-02)
```

#### 3. `frontend/src/components/inquiry/InquiryAnswerTab.tsx`

**이관 대상:**
- 톤 선택 (정중체/기술 상세/요약) — labelTone() 사용
- 채널 선택 (이메일/메신저) — labelChannel() 사용
- "답변 초안 생성" 버튼
- 답변 초안 표시:
  - 버전 + 상태 배지 + 채널 + 톤
  - 타임라인 (초안 → 검토 완료 → 승인 완료 → 발송 완료) — labelAnswerStatus() 사용
  - 판정 + 신뢰도
  - 답변 본문
  - 출처 (citations)
  - 리스크 플래그 (labelRiskFlag)
  - 형식 경고
- 리뷰 행: 리뷰어 입력 + 코멘트 + 리뷰 버튼
- 승인 행: 승인자 입력 + 코멘트 + 승인 버튼
- 발송 행: 발송자 입력 + 발송 버튼

```typescript
interface InquiryAnswerTabProps {
  inquiryId: string;
}

// 내부에서 draftInquiryAnswer(), getLatestAnswerDraft(),
// reviewAnswerDraft(), approveAnswerDraft(), sendAnswerDraft() 호출
// Badge, Toast 컴포넌트 사용
```

#### 4. `frontend/src/components/inquiry/InquiryHistoryTab.tsx`

**이관 대상:**
- 버전 히스토리 목록 (listAnswerDraftHistory)
  - 각 버전: v{N}, 상태, 판정, 채널, 톤
- 감사 로그 조회 (향후 audit-logs API 연동 시 확장)

```typescript
interface InquiryHistoryTabProps {
  inquiryId: string;
}

// DataTable 컴포넌트 사용
// Badge로 상태/판정 표시
```

### 상세 페이지 조립

**수정 파일:** `frontend/src/app/inquiries/[id]/page.tsx`

Sprint 7에서 만든 골격에 실제 탭 컴포넌트를 연결:

```typescript
'use client';

import { useParams } from 'next/navigation';
import { Tabs } from '@/components/ui/Tabs';
import InquiryInfoTab from '@/components/inquiry/InquiryInfoTab';
import InquiryAnalysisTab from '@/components/inquiry/InquiryAnalysisTab';
import InquiryAnswerTab from '@/components/inquiry/InquiryAnswerTab';
import InquiryHistoryTab from '@/components/inquiry/InquiryHistoryTab';

export default function InquiryDetailPage() {
  const { id } = useParams<{ id: string }>();

  return (
    <div className="stack">
      <h2>문의 상세</h2>
      <Tabs
        defaultTab="info"
        tabs={[
          { key: 'info',     label: '기본 정보', content: <InquiryInfoTab inquiryId={id} /> },
          { key: 'analysis', label: '분석',      content: <InquiryAnalysisTab inquiryId={id} /> },
          { key: 'answer',   label: '답변',      content: <InquiryAnswerTab inquiryId={id} /> },
          { key: 'history',  label: '이력',      content: <InquiryHistoryTab inquiryId={id} /> },
        ]}
      />
    </div>
  );
}
```

### 기존 파일 정리

**수정 파일:** `frontend/src/components/inquiry-form.tsx`

분해 완료 후 **문의 생성 폼만** 남기고 나머지 삭제:
- 문의 생성 관련 코드만 유지 (질문 입력 + 채널 선택 + 문서 업로드 + 제출)
- 조회/분석/초안/리뷰/승인/발송 코드 모두 제거
- 파일명을 `InquiryCreateForm.tsx`로 변경 권장

**수정 파일:** `frontend/src/app/inquiries/new/page.tsx`

```typescript
import InquiryCreateForm from '@/components/inquiry/InquiryCreateForm';

export default function NewInquiryPage() {
  return <InquiryCreateForm />;
}
```

### 수용 기준

- [ ] `/inquiries/{id}`에서 4개 탭이 모두 동작한다
- [ ] 기본 정보 탭: 문의 정보 + 문서 목록 + 인덱싱 실행
- [ ] 분석 탭: 근거 검색 + 판정 결과 (출처 구분 포함)
- [ ] 답변 탭: 초안 생성 + 리뷰 + 승인 + 발송 전체 워크플로우
- [ ] 이력 탭: 버전 히스토리 목록
- [ ] inquiry-form.tsx가 생성 폼만 남기고 정리됨
- [ ] 기존 기능(등록→인덱싱→분석→초안→승인→발송) **전체 회귀 없음**

---

## FE-04. 대시보드 리디자인 (P1)

### 목적

기존 대시보드에 **최근 문의 요약**을 추가하고 레이아웃을 개선한다.

### 작업 내용

**수정 파일:** `frontend/src/app/dashboard/page.tsx`

#### 화면 구조

```
┌──────────────────────────────────────────────────────────┐
│ 운영 대시보드                                              │
├───────────────┬───────────────┬───────────────────────────┤
│ 발송 성공률    │ 중복 차단률    │ Fallback 비율              │
│   85.7%       │   12.3%       │   4.2%                    │
│ 17/20건       │ 3/24건        │ 2/48건                    │
├───────────────┴───────────────┴───────────────────────────┤
│                                                          │
│ 최근 문의 (5건)                                [전체 보기 →]│
│ ┌────────┬───────────────┬──────┬──────┬──────┐          │
│ │ 접수일  │ 질문 요약      │ 채널 │ 상태  │ 답변  │          │
│ │ 02-13  │ Reagent X...  │이메일│접수됨│승인완료│          │
│ │ 02-12  │ Protocol Y... │메신저│분석완료│ 초안 │          │
│ │ ...    │ ...           │ ... │ ...  │ ...  │          │
│ └────────┴───────────────┴──────┴──────┴──────┘          │
│                                                          │
│ 최근 실패 사유 Top                                         │
│ • 네트워크 타임아웃 (3건)                                   │
│ • 형식 오류 (2건)                                          │
└──────────────────────────────────────────────────────────┘
```

**변경 사항:**
1. 메트릭 카드 3열 배치 (기존 2열)
2. **최근 문의 5건** 섹션 추가 — `listInquiries({ size: 5 })` 호출
3. "전체 보기" 링크 → `/inquiries`
4. DataTable 컴포넌트 사용
5. 실패 사유를 하단으로 이동

### 수용 기준

- [ ] 메트릭 카드 3열 배치
- [ ] 최근 문의 5건이 표시된다 (한국어 라벨)
- [ ] "전체 보기" 클릭 시 `/inquiries` 이동
- [ ] 데이터 로딩 중 스피너 표시

---

## FE-05. 반응형 레이아웃 (P1)

### 작업 내용

**수정 파일:** `frontend/src/app/globals.css`

#### 브레이크포인트 정의

| 이름 | 범위 | 대상 |
|------|------|------|
| Desktop | 1280px+ | 기본 레이아웃 |
| Tablet | 768px ~ 1279px | 2열 → 1열, 테이블 스크롤 |
| Mobile | ~767px | 단일 열, 네비 접힘 |

#### 주요 반응형 규칙

```css
/* 태블릿 */
@media (max-width: 1279px) {
  .metrics-grid { grid-template-columns: 1fr 1fr; }
  .filter-bar { flex-direction: column; align-items: stretch; }
}

/* 모바일 */
@media (max-width: 767px) {
  .metrics-grid { grid-template-columns: 1fr; }
  .data-table { display: block; overflow-x: auto; }
  .tabs-header { overflow-x: auto; }
  .nav-links { flex-wrap: wrap; gap: var(--space-xs); }
  .topbar { flex-direction: column; align-items: flex-start; }
}
```

### 수용 기준

- [ ] 1280px에서 정상 렌더링 (기본)
- [ ] 768px에서 필터/테이블이 1열로 전환
- [ ] 테이블이 가로 스크롤 가능

---

## FE-06. 접근성 개선 (P1)

### 작업 내용

| 항목 | 대상 | 조치 |
|------|------|------|
| 키보드 네비게이션 | 탭, 버튼, 테이블 행 | `tabIndex`, `onKeyDown(Enter)` |
| 포커스 표시 | 모든 인터랙티브 요소 | `:focus-visible` outline 스타일 |
| ARIA 라벨 | 네비게이션, 탭, 테이블 | `aria-label`, `role="tablist"`, `role="tab"` |
| 색상 대비 | 텍스트/배경 | WCAG AA 기준 (4.5:1) 충족 확인 |
| 스크린 리더 | 상태 변경 | `aria-live="polite"` 유지/확장 |

**수정 대상 파일:**
- `Tabs.tsx` → `role="tablist"`, `role="tab"`, `aria-selected`
- `DataTable.tsx` → `role="table"`, 키보드 행 이동
- `Badge.tsx` → `aria-label` (상태 설명)
- `Toast.tsx` → `role="alert"`, `aria-live="assertive"`

```css
/* 포커스 표시 */
*:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}
```

### 수용 기준

- [ ] Tab 키로 모든 인터랙티브 요소 순회 가능
- [ ] 탭 컴포넌트에서 좌/우 화살표로 탭 전환 가능
- [ ] 포커스 표시가 시각적으로 명확함

---

## 실행 순서

```
Week 1:
  1) FE-01  디자인 토큰 정리 (0.5일)
  2) FE-02  공통 컴포넌트 7개 추출 (2일)
     ├── Badge, DataTable, Pagination
     ├── Tabs, Toast, EmptyState, FilterBar
     └── CSS 추가
  3) FE-03  탭 컴포넌트 분해 시작 (2일)
     ├── InquiryInfoTab
     ├── InquiryAnalysisTab
     └── InquiryAnswerTab + InquiryHistoryTab

Week 2:
  4) FE-03  상세 페이지 조립 + inquiry-form.tsx 정리 (1일)
  5) FE-04  대시보드 리디자인 (1일)
  6) FE-05  반응형 레이아웃 (1일)
  7) FE-06  접근성 개선 (0.5일)
  8) QA     전체 화면 스모크 테스트 + 스크린샷 비교 (1일)
```

---

## 수용 기준 (Sprint 전체)

1. 공통 UI 컴포넌트 **7개**가 `src/components/ui/`에 생성되어 재사용된다
2. `/inquiries/{id}` 상세 페이지에 **4개 탭** (기본 정보·분석·답변·이력)이 모두 동작한다
3. `inquiry-form.tsx`가 문의 생성 폼으로만 축소됨 (기존 500+ LOC → ~150 LOC)
4. 대시보드에 최근 문의 5건이 표시된다
5. 디자인 토큰 기반으로 모든 CSS가 통일된다
6. 768px 태블릿 해상도에서 정상 렌더링
7. 키보드 네비게이션으로 주요 워크플로우 수행 가능
8. **기존 기능 전체 회귀 없음**
9. `./gradlew build` + `npm run build` 모두 성공

---

## 부록: 파일 생성/수정 체크리스트

### 신규 파일

```
frontend/src/components/ui/Badge.tsx
frontend/src/components/ui/DataTable.tsx
frontend/src/components/ui/Pagination.tsx
frontend/src/components/ui/Tabs.tsx
frontend/src/components/ui/Toast.tsx
frontend/src/components/ui/EmptyState.tsx
frontend/src/components/ui/FilterBar.tsx
frontend/src/components/inquiry/InquiryInfoTab.tsx
frontend/src/components/inquiry/InquiryAnalysisTab.tsx
frontend/src/components/inquiry/InquiryAnswerTab.tsx
frontend/src/components/inquiry/InquiryHistoryTab.tsx
frontend/src/components/inquiry/InquiryCreateForm.tsx   (inquiry-form.tsx에서 리네임)
```

### 수정 파일

```
frontend/src/app/globals.css                     (디자인 토큰 확장 + 컴포넌트 CSS)
frontend/src/app/dashboard/page.tsx              (리디자인 + 최근 문의)
frontend/src/app/inquiries/[id]/page.tsx         (탭 컴포넌트 연결)
frontend/src/app/inquiries/new/page.tsx          (InquiryCreateForm 사용)
frontend/src/components/app-shell-nav.tsx         (활성 메뉴 로직 유지)
```

### 삭제 파일

```
frontend/src/components/inquiry-form.tsx          (InquiryCreateForm.tsx로 대체)
frontend/src/app/inquiry/new/page.tsx             (리다이렉트만 유지 또는 삭제)
```
