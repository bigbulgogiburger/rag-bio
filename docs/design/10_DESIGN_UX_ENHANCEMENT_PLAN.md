# 디자인 & UX/UI 고도화 계획

> 현재 디자인 현대화(Phase 1~2) 이후, 추가로 고도화할 수 있는 영역을 전수 조사한 결과입니다.

---

## 1. 컴포넌트 시스템 고도화

### 1-1. Button — 로딩/인터랙션 강화

| 현재 | 개선 |
|------|------|
| disabled 시 `opacity-50`만 | Loader2 스피너 + 텍스트 변경 패턴 통일 |
| Icon 버튼 `h-9 w-9` (36px) | `h-10 w-10` (40px) — 모바일 터치 타겟 |
| hover 시 배경색만 변경 | `hover:scale-[1.02] active:scale-[0.98]` 마이크로 모션 |

**파일**: `src/components/ui/button.tsx`

### 1-2. Badge — 접근성 + 애니메이션

| 현재 | 개선 |
|------|------|
| 색상으로만 상태 구분 | 아이콘(체크, X, 스피너) 추가로 색맹 사용자 대응 |
| 도트 인디케이터 정적 | "진행 중" 상태에 `animate-pulse` 적용 |
| `text-[0.7rem]` (11px) | 최소 `text-xs` (12px) — 모바일 가독성 |

**파일**: `src/components/ui/Badge.tsx`

### 1-3. Skeleton — Shimmer 효과 적용

| 현재 | 개선 |
|------|------|
| `animate-pulse` 단독 | `animate-shimmer` 그라데이션 효과 (이미 globals.css에 정의됨, 미연결) |
| `bg-muted` | `bg-muted/60` — 더 자연스러운 대비 |

**파일**: `src/components/ui/skeleton.tsx`
**참조**: `globals.css:237-249` (shimmer 키프레임 이미 존재)

### 1-4. Input — 포커스 링 강화

| 현재 | 개선 |
|------|------|
| `ring-1` (1px) — 너무 얇음 | `ring-2` + `ring-primary/25` |
| placeholder 대비 낮음 | placeholder 밝기를 55%로 조정 |
| 비밀번호 입력 | 보기/숨기기 토글 버튼 추가 |

**파일**: `src/components/ui/input.tsx`, `src/components/auth/LoginForm.tsx`

### 1-5. DataTable — 행 인터랙션 + 모바일

| 현재 | 개선 |
|------|------|
| 행 hover `bg-primary/5` (너무 약함) | `bg-accent` (더 확실한 피드백) |
| 키보드 포커스 없음 | `focus-visible:ring-2` on `<tr>` |
| 가로 스크롤 힌트 없음 | 우측 그라데이션 페이드 오버레이 |
| 행에 `aria-label` 없음 | 각 행에 핵심 데이터 기반 `aria-label` 추가 |

**파일**: `src/components/ui/DataTable.tsx`

### 1-6. Tabs — 전환 애니메이션

| 현재 | 개선 |
|------|------|
| 탭 전환 시 즉시 교체 | `animate-in fade-in duration-200` 적용 |
| 비활성 탭 대비 약함 | `text-muted-foreground` 밝기를 50%로 조정 |
| 활성 언더라인 단색 | 그라데이션 페이드 언더라인 |

**파일**: `src/components/ui/Tabs.tsx`

---

## 2. 문의 상세 탭 고도화 (가장 복잡한 페이지)

### 2-1. InquiryAnswerTab — 스트리밍 UX

| 현재 | 개선 |
|------|------|
| 스트리밍 커서 `\25CD` (원형) | `|` 깜빡이는 파이프 커서 |
| 근거 토글 시 아이콘 없음 | ChevronDown 회전 애니메이션 |
| 스트리밍 중 자동 스크롤 없음 | `streamingRef.scrollIntoView()` 자동 스크롤 |
| 인용 링크 구분 없음 | `underline decoration-primary/30` 스타일 |

**파일**: `src/components/inquiry/InquiryAnswerTab.tsx`

### 2-2. AnswerEditor — 모바일 도구모음

| 현재 | 개선 |
|------|------|
| 모든 버튼 한 줄 — 모바일 넘침 | 모바일에서 `overflow-x-auto scrollbar-none` |
| `gap-1` — 터치 어려움 | `gap-1.5` + 그룹 구분선 |
| 에디터 포커스 피드백 없음 | ProseMirror 래퍼에 `focus-within:ring-2` |
| 링크 삽입 `window.prompt()` | 커스텀 Dialog 컴포넌트 |

**파일**: `src/components/inquiry/AnswerEditor.tsx`

### 2-3. PipelineProgress — 단계 가시성

| 현재 | 개선 |
|------|------|
| 단계 라벨 `text-[9px]` | 최소 `text-xs` (12px) |
| 에러 상태 텍스트만 | AlertCircle 아이콘 추가 |
| 연결 끊김 시 "대기" 표시 없음 | "연결 재시도 중..." 상태 추가 |

**파일**: `src/components/inquiry/PipelineProgress.tsx`

### 2-4. WorkflowResultCard — 시맨틱 색상 통일

| 현재 | 개선 |
|------|------|
| `border-emerald-300 bg-emerald-50` 하드코딩 | `border-success-border bg-success-light` 토큰 |
| `border-amber-300 bg-amber-50` 하드코딩 | `border-warning-border bg-warning-light` 토큰 |
| `border-red-300 bg-red-50` 하드코딩 | `border-danger-border bg-danger-light` 토큰 |
| Issue 목록 5개 잘림 — 무음 | "더 보기" 버튼 추가 |

**파일**: `src/components/inquiry/WorkflowResultCard.tsx`

### 2-5. InquiryInfoTab — 인덱싱 UI

| 현재 | 개선 |
|------|------|
| 프로그레스 바 `border-blue-500/30` 하드코딩 | `border-info-border bg-info-light` 토큰 |
| 프로그레스 변화 애니메이션 없음 | `transition-all duration-500` |
| 에러 인라인 표시 — 테이블 높이 불균형 | 접기/펼치기 디테일 행 |

**파일**: `src/components/inquiry/InquiryInfoTab.tsx`

---

## 3. 차트 & 시각화 고도화

### 3-1. TimelineChart — 테마 연동

| 현재 | 개선 |
|------|------|
| 라인 색상 `hsl(221, 83%, 53%)` 하드코딩 | CSS 변수 `hsl(var(--primary))` 참조 |
| 툴팁 배경 하드코딩 | `hsl(var(--card))` 사용 |
| 데이터 없을 때 텍스트만 | EmptyState 컴포넌트 사용 |

**파일**: `src/components/dashboard/TimelineChart.tsx`

### 3-2. StatusPieChart — 동일 이슈

**파일**: `src/components/dashboard/StatusPieChart.tsx`

---

## 4. 다크 모드 정밀 조정

### 4-1. 대비 문제

| 토큰 | 현재 (Dark) | 문제 | 제안 |
|------|------------|------|------|
| `--border` | `217 33% 22%` | 카드 border 거의 안 보임 | `217 33% 26%` |
| `--input` | `217 33% 22%` | border와 동일, 입력 필드 구분 안 됨 | `217 33% 20%` (더 어둡게) |
| `--muted-foreground` | `215 20% 65%` | 보조 텍스트 너무 밝음 | `215 20% 60%` |
| `--secondary` | `217 33% 17%` | 배경과 거의 동일 | `217 33% 19%` |

**파일**: `src/app/globals.css` (`.dark` 블록)

### 4-2. 차트 색상

Recharts 컴포넌트가 CSS 변수가 아닌 하드코딩 HSL을 사용하므로, 다크 모드에서 밝은 색상으로 자동 전환되지 않음.

---

## 5. 모바일 UX 고도화

### 5-1. 터치 타겟 감사

44px 미달 요소들:

| 컴포넌트 | 현재 크기 | 위치 |
|---------|---------|------|
| Pagination 번호 버튼 | `min-w-[32px] h-8` (32px) | `Pagination.tsx` |
| Pagination 화살표 아이콘 | `h-3.5 w-3.5` (14px) | `Pagination.tsx` |
| Button `icon` variant | `h-9 w-9` (36px) | `button.tsx` |
| Editor 도구 버튼 | `h-8 w-8` (32px) | `AnswerEditor.tsx` |
| FilterBar 라벨 | 클릭 불가 영역 | `FilterBar.tsx` |

### 5-2. 모바일 전용 패턴 추가

| 패턴 | 적용 대상 | 설명 |
|------|---------|------|
| Swipe-to-action | 문의 목록 카드 | 좌 스와이프로 빠른 액션 (삭제, 답변 보기) |
| 스크롤 위치 복원 | 문의 목록 ↔ 상세 | `sessionStorage` 기반 |
| 키보드 높이 대응 | 문의 작성 폼 | `visualViewport` API로 CTA 위치 조정 |
| 가로 스크롤 힌트 | DataTable, RAG 메트릭 | 우측 그라데이션 페이드 오버레이 |

### 5-3. Bottom Sheet 통일

| 현재 | 개선 |
|------|------|
| KB 상세 모달만 Bottom Sheet | 모든 모달을 Bottom Sheet 패턴으로 통일 |
| `window.confirm()` 사용 (KB 삭제) | 커스텀 ConfirmDialog 컴포넌트 |
| 업로드 모달 중앙 정렬 | 모바일에서 Bottom Sheet로 전환 |

---

## 6. 접근성 (A11y) 고도화

### 6-1. ARIA 보강

| 컴포넌트 | 현재 | 개선 |
|---------|------|------|
| DataTable 행 | `role="button"` 있으나 `aria-label` 없음 | 핵심 데이터 기반 라벨 추가 |
| FilterBar select | `<label>` 암시적 연결 | `htmlFor` 명시적 연결 |
| 모달 | `role="dialog"` 있으나 포커스 트랩 없음 | `FocusTrap` 추가 |
| Toast | Sonner 기본 동작 | `role="alert" aria-live="polite"` 검증 |
| Badge 색상 | 색상만으로 구분 | 아이콘 + 텍스트 병행 |

### 6-2. 키보드 내비게이션

| 컴포넌트 | 현재 | 개선 |
|---------|------|------|
| DataTable 행 | Tab으로 이동 가능 | `focus-visible` 링 추가 |
| Tabs | Radix 기반 양호 | 유지 |
| 모달 닫기 | Escape 키 지원 | 포커스 복원 추가 |
| 에디터 도구 | 키보드 단축키 있음 | 단축키 힌트 툴팁 표시 |

---

## 7. 마이크로 인터랙션 & 모션

### 7-1. 입장 애니메이션

| 요소 | 애니메이션 |
|------|----------|
| 페이지 전환 | `animate-in fade-in duration-200` |
| 메트릭 카드 | staggered `slide-up-fade` (50ms 딜레이) |
| 모달 열림 | `slide-in-from-bottom-4 fade-in duration-300` |
| Toast 알림 | 이미 `toast-in` 있음 — 유지 |

### 7-2. 숫자 카운트업

대시보드 메트릭 값(0%, 100건 등)을 0에서 실제 값까지 카운트업 애니메이션.

```tsx
// 가벼운 커스텀 훅
function useCountUp(target: number, duration = 800) {
  const [value, setValue] = useState(0);
  useEffect(() => {
    const start = performance.now();
    const step = (now: number) => {
      const progress = Math.min((now - start) / duration, 1);
      setValue(Math.floor(progress * target));
      if (progress < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }, [target]);
  return value;
}
```

### 7-3. 스크롤 기반 효과

| 요소 | 효과 |
|------|------|
| 헤더 | 스크롤 시 `shadow-brand` → `shadow-brand-lg` 전환 |
| 메트릭 카드 | 뷰포트 진입 시 `slide-up-fade` 트리거 |
| CTA sticky | 스크롤 임계값 이후 표시 |

---

## 8. 성능 최적화

### 8-1. 이미지 최적화

| 현재 | 개선 |
|------|------|
| `<img>` 직접 사용 | Next.js `<Image>` 컴포넌트 활용 |
| lazy loading 미적용 | `loading="lazy" decoding="async"` 추가 |
| SVG 인라인 반복 | 공통 아이콘 컴포넌트 추출 |

### 8-2. 번들 사이즈

| 현재 | 개선 |
|------|------|
| `/inquiries/[id]` 309KB | AnswerEditor dynamic import 이미 적용 (양호) |
| Recharts 전체 import | `import { LineChart, ... } from 'recharts'` 트리쉐이킹 확인 |
| Lucide 전체 import | 개별 import 패턴 사용 중 (양호) |

### 8-3. CLS 방지

| 요소 | 개선 |
|------|------|
| 메트릭 카드 | 스켈레톤과 실제 카드 높이 동일하게 |
| 차트 | `aspect-ratio` 또는 min-height 지정 |
| Badge | 고정 너비 또는 `min-w` 지정 |

---

## 실행 우선순위

### P0 — 즉시 (시각적 임팩트 큼, 작업량 작음)
1. Skeleton shimmer 연결 (1건, 10분)
2. Dark 모드 대비 조정 (globals.css, 15분)
3. WorkflowResultCard 하드코딩 색상 → 토큰 (1건, 20분)
4. Button hover 마이크로 모션 추가 (1건, 10분)

### P1 — 단기 (UX 체감 개선)
5. 차트 색상 CSS 변수 연동
6. DataTable 행 hover/focus 강화
7. AnswerEditor 모바일 도구모음 스크롤
8. 모달 → Bottom Sheet 통일

### P2 — 중기 (완성도 향상)
9. 메트릭 카운트업 애니메이션
10. 페이지 전환 fade-in
11. 스크롤 기반 헤더 shadow 전환
12. 터치 타겟 44px 감사 및 수정

### P3 — 장기 (폴리시)
13. Swipe-to-action 모바일 패턴
14. 스크롤 위치 복원 (`useScrollRestore`)
15. 키보드 높이 대응 (`visualViewport`)
16. ConfirmDialog 컴포넌트 (`window.confirm` 대체)
