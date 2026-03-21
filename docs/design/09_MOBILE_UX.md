# 09. 모바일 전용 UX 패턴

---

## 현재 모바일 대응 분석

### 잘 되어 있는 것
- `@media (pointer: coarse)` → 44px 최소 터치 타겟
- `safe-area-inset-bottom` → 노치 디바이스 대응
- `hidden md:block` / `md:hidden` → 데스크톱/모바일 분리
- Bottom Nav 4항목 → 적정 개수
- `overflow-x: hidden; max-width: 100vw;` → 가로 스크롤 방지
- `-webkit-overflow-scrolling: touch;` → 부드러운 스크롤

### 개선이 필요한 것

| 영역 | 현재 상태 | 문제 |
|------|---------|------|
| 모바일 카드 | 데스크톱 테이블의 축소판 | 고유한 모바일 패턴 없음 |
| 스와이프 | 미구현 | 카드 스와이프로 빠른 액션 가능 |
| Pull-to-Refresh | 미구현 | 목록 페이지에서 기대되는 패턴 |
| 바텀 시트 | `window.confirm` 사용 | 네이티브 앱 느낌 부족 |
| 키보드 대응 | 미구현 | textarea 포커스 시 뷰포트 축소 |
| 가로 모드 | 미고려 | 태블릿 사용 시 레이아웃 깨짐 가능 |
| 로딩 상태 | 텍스트만 | 스켈레톤은 있으나 모바일 최적화 안 됨 |
| Haptic | 미구현 | 성공/에러 시 진동 피드백 |
| 스크롤 위치 | 미보존 | 상세→목록 복귀 시 맨 위로 |

---

## 패턴별 개선 제안

### 1. 모바일 카드 — 터치 최적화

```tsx
{/* 현재: border-border/50 bg-muted/20 p-4 */}
{/* 변경: 터치 피드백 + 시각적 깊이 */}

<button
  className="w-full rounded-2xl border border-border/30 bg-card p-4
             text-left shadow-brand
             transition-all duration-150
             active:scale-[0.98] active:shadow-none"
>
  {/* 카드 내용 */}
</button>
```

**핵심 원칙:**
- `rounded-2xl` (모바일은 더 둥근 모서리가 자연스러움)
- `active:scale-[0.98]` (터치 피드백)
- `shadow-brand` (카드 부유감)
- 최소 높이 72px (편한 터치)

### 2. Bottom Sheet 패턴

`window.confirm` 대체 + 상세 모달에 통일 적용:

```tsx
interface BottomSheetProps {
  open: boolean;
  onClose: () => void;
  title?: string;
  children: React.ReactNode;
}

function BottomSheet({ open, onClose, title, children }: BottomSheetProps) {
  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 md:flex md:items-center md:justify-center">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-[hsl(var(--overlay))/50] backdrop-blur-sm
                   animate-in fade-in duration-200"
        onClick={onClose}
      />

      {/* Sheet */}
      <div className="absolute bottom-0 left-0 right-0
                      md:relative md:bottom-auto md:max-w-lg md:mx-auto
                      rounded-t-2xl md:rounded-2xl bg-card border shadow-2xl
                      animate-in slide-in-from-bottom duration-300
                      max-h-[85vh] overflow-y-auto">
        {/* 드래그 핸들 (모바일) */}
        <div className="flex justify-center py-2 md:hidden">
          <div className="h-1 w-8 rounded-full bg-muted-foreground/30" />
        </div>

        {title && (
          <div className="px-6 py-3 border-b">
            <h3 className="text-lg font-semibold">{title}</h3>
          </div>
        )}

        <div className="p-6">{children}</div>
      </div>
    </div>
  );
}
```

### 3. 목록 페이지 Pull-to-Refresh 힌트

실제 PTR 구현은 복잡하므로, 시각적 힌트만:

```tsx
{/* 목록 상단에 마지막 새로고침 시간 + 수동 버튼 */}
<div className="flex items-center justify-between px-1 py-2 md:hidden">
  <p className="text-[0.6rem] text-muted-foreground">
    {lastRefreshed
      ? `${formatRelativeTime(lastRefreshed)} 업데이트`
      : ""}
  </p>
  <button
    onClick={fetchInquiries}
    className="text-xs text-primary font-medium"
    disabled={loading}
  >
    {loading ? "갱신 중..." : "새로고침"}
  </button>
</div>
```

### 4. 스크롤 위치 보존

```tsx
// hooks/useScrollRestore.ts
import { useEffect } from "react";

export function useScrollRestore(key: string) {
  useEffect(() => {
    const saved = sessionStorage.getItem(`scroll-${key}`);
    if (saved) {
      window.scrollTo(0, parseInt(saved, 10));
    }

    const handleScroll = () => {
      sessionStorage.setItem(`scroll-${key}`, String(window.scrollY));
    };

    window.addEventListener("scroll", handleScroll, { passive: true });
    return () => window.removeEventListener("scroll", handleScroll);
  }, [key]);
}

// 사용: InquiriesPage
useScrollRestore("inquiries-list");
```

### 5. 키보드 대응 — 폼 페이지

```tsx
// InquiryCreateForm 개선
// 키보드가 올라올 때 CTA 버튼 위치 조정

useEffect(() => {
  if (typeof visualViewport === "undefined") return;

  const handleResize = () => {
    const bottom = window.innerHeight - visualViewport!.height;
    document.documentElement.style.setProperty(
      "--keyboard-height",
      `${bottom}px`
    );
  };

  visualViewport!.addEventListener("resize", handleResize);
  return () => visualViewport!.removeEventListener("resize", handleResize);
}, []);

// Sticky CTA에 적용
<div className="sticky z-10 -mx-4 bg-card px-4 py-3 sm:static ..."
     style={{ bottom: "calc(5rem + var(--keyboard-height, 0px))" }}>
```

### 6. 모바일 네비게이션 개선

#### Bottom Nav 활성 인디케이터

```tsx
{/* Material Design 3 스타일 */}
<Link href={href} className="flex flex-1 flex-col items-center gap-0.5 py-2">
  <div className={cn(
    "relative flex h-8 w-16 items-center justify-center rounded-2xl transition-all",
    isActive
      ? "bg-primary/12"
      : "bg-transparent"
  )}>
    <Icon className={cn(
      "h-5 w-5 transition-colors",
      isActive ? "text-primary" : "text-muted-foreground"
    )} />
  </div>
  <span className={cn(
    "text-[0.6rem] font-medium transition-colors",
    isActive ? "text-primary" : "text-muted-foreground"
  )}>
    {shortLabel}
  </span>
</Link>
```

### 7. 모바일 테이블 → 카드 전환 기준

```
≤ 640px (sm 미만): 항상 카드 리스트
641px ~ 767px (sm): 카드 리스트 (선택적 테이블)
≥ 768px (md): DataTable

카드 리스트 패턴:
- 한 카드에 핵심 정보 3줄 이내
- 상단: 제목/질문 (font-medium, line-clamp-2)
- 중단: 핵심 상태 Badge
- 하단: 메타 정보 (날짜, 채널 등) — text-xs muted
```

### 8. 모바일 차트 대응

대시보드 차트는 모바일에서 세로 배치 + 축소:

```tsx
{/* 차트 높이 모바일 대응 */}
<div className="h-[200px] sm:h-[300px]">
  <TimelineChart ... />
</div>

{/* 파이 차트는 모바일에서 범례를 하단으로 */}
<div className="flex flex-col items-center gap-4">
  <div className="h-[180px] w-[180px] sm:h-[220px] sm:w-[220px]">
    <StatusPieChart ... />
  </div>
  {/* 범례 */}
</div>
```

---

## 페이지별 모바일 체크리스트

### 공통
- [x] `min-h-[44px]` 터치 타겟
- [x] `safe-area-inset-bottom` 하단 여백
- [x] `overflow-x: hidden` 가로 스크롤 방지
- [ ] 스크롤 위치 보존
- [ ] 키보드 높이 대응
- [ ] Bottom Sheet 패턴 통일
- [ ] `active:scale` 터치 피드백

### 대시보드
- [x] 메트릭 카드 2열
- [x] 차트 1열 종배치
- [x] 모바일 카드 리스트
- [ ] 차트 높이 축소
- [ ] 메트릭 애니메이션

### 문의 목록
- [x] 모바일 카드 리스트
- [ ] 상태 칩 필터 (가로 스크롤)
- [ ] FAB 문의 작성 버튼
- [ ] Pull-to-refresh 힌트

### 문의 작성
- [x] Sticky CTA 버튼
- [ ] 제품군 칩 토글
- [ ] Drop Zone 컴팩트화
- [ ] 키보드 대응

### 문의 상세
- [x] 탭 네비게이션
- [x] PDF 풀스크린 모달
- [ ] 에디터 도구모음 스크롤
- [ ] 워크플로 바 컴팩트

### 지식 기반
- [x] 모바일 카드 리스트
- [x] FAB 업로드 버튼
- [ ] 상세 Bottom Sheet
- [ ] 비율 바 시각화

### 로그인
- [ ] 전체 화면 폼 (Header 숨김)
- [ ] 키보드 대응
- [ ] 자동완성 속성

---

## CSS 유틸리티 추가 제안

```css
/* globals.css에 추가 */

/* 스크롤바 숨김 (칩 스크롤 등) */
.scrollbar-none {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
.scrollbar-none::-webkit-scrollbar {
  display: none;
}

/* 미세 펄스 (인덱싱 상태) */
@keyframes pulse-subtle {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.85; }
}
.animate-pulse-subtle {
  animation: pulse-subtle 2s ease-in-out infinite;
}

/* 입장 애니메이션 */
@keyframes slide-up-fade {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
.animate-slide-up {
  animation: slide-up-fade 0.3s ease-out;
}

/* 키보드 높이 변수 */
:root {
  --keyboard-height: 0px;
}
```

---

## 성능 고려사항

- 모바일 카드의 `backdrop-blur`는 GPU 부하 → Bottom Nav에만 사용
- `active:scale` 트랜지션은 `transform`만 사용 → GPU 가속
- 이미지 lazy loading → `loading="lazy" decoding="async"`
- 차트 라이브러리 (Recharts) → 모바일에서 데이터 포인트 축소 고려
- PDF 뷰어 → 모바일에서 1페이지씩 렌더링 (현재 구현 양호)
