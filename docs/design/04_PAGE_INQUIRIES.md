# 04. 문의 목록 페이지 현대화

**경로:** `/inquiries`
**파일:** `src/app/inquiries/page.tsx`

---

## 현재 상태

### 레이아웃
```
[문의 대응 내역]                         [문의 작성 버튼]
┌─────────────────────────────────────────┐
│ FilterBar (상태, 채널, 시작일, 종료일, 검색어) │
│─────────────────────────────────────────│
│ DataTable (접수일, 질문요약, 채널, 상태, 답변, 문서수) │
│─────────────────────────────────────────│
│ Pagination                              │
└─────────────────────────────────────────┘
```

### 문제점

1. **페이지 헤더 단순함**: `text-xl` 제목 + 버튼만, 문맥 정보 없음
2. **FilterBar 시각적 무게감**: 필터가 5개 → 한 줄에 넘침, 모바일에서 복잡
3. **테이블 행 호버**: `hover:bg-primary/5`만 — 더 분명한 피드백 필요
4. **모바일 카드**: 데스크톱 축소판 — 탭/스와이프 등 모바일 패턴 미활용
5. **빈 상태**: EmptyState 컴포넌트는 양호하나, 첫 사용자 가이드 부족
6. **상태 필터 시각화**: Select 드롭다운만 — 빠른 필터링을 위한 칩/탭 없음

---

## 현대화 설계

### 1. 페이지 헤더 + 빠른 통계

```tsx
<div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
  <div>
    <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">
      문의 대응 내역
    </h2>
    <p className="text-sm text-muted-foreground mt-1">
      {response
        ? `총 ${response.totalElements}건의 문의`
        : "문의 목록을 불러오는 중..."
      }
    </p>
  </div>
  <Button onClick={() => router.push("/inquiries/new")} className="hidden sm:inline-flex">
    문의 작성
  </Button>
</div>
```

### 2. 상태 필터 — 칩 그룹 추가

FilterBar 위에 빠른 상태 필터 칩을 추가:

```tsx
{/* Quick Status Chips — FilterBar 위 */}
<div className="flex items-center gap-2 overflow-x-auto pb-2 -mx-4 px-4 sm:mx-0 sm:px-0
                scrollbar-none">
  {[
    { value: "", label: "전체", count: response?.totalElements },
    { value: "RECEIVED", label: "접수", count: statusCounts?.received },
    { value: "ANALYZED", label: "분석완료", count: statusCounts?.analyzed },
    { value: "ANSWERED", label: "답변완료", count: statusCounts?.answered },
  ].map((chip) => (
    <button
      key={chip.value}
      onClick={() => { handleFilterChange("status", chip.value); handleSearch(); }}
      className={cn(
        "flex items-center gap-1.5 rounded-full border px-3 py-1.5",
        "text-xs font-medium whitespace-nowrap transition-all",
        filters.status === chip.value
          ? "border-primary bg-primary/10 text-primary"
          : "border-border bg-card text-muted-foreground hover:border-primary/30"
      )}
    >
      {chip.label}
      {chip.count !== undefined && (
        <span className="tabular-nums">{chip.count}</span>
      )}
    </button>
  ))}
</div>
```

### 3. FilterBar 개선 — 접기/펼치기

모바일에서 FilterBar가 복잡하므로, 기본 숨김 + 토글:

```tsx
<div className="rounded-xl border bg-card shadow-sm">
  {/* Quick Filter Chips (항상 표시) */}
  <div className="p-4 pb-2">
    {/* 칩 그룹 */}
  </div>

  {/* 상세 필터 (접기/펼치기) */}
  <div className="border-t border-border/50">
    <button
      onClick={() => setShowFilters(!showFilters)}
      className="flex w-full items-center justify-between px-4 py-2.5
                 text-xs font-medium text-muted-foreground hover:text-foreground"
    >
      상세 필터
      <ChevronIcon className={cn("h-4 w-4 transition-transform", showFilters && "rotate-180")} />
    </button>
    {showFilters && (
      <div className="px-4 pb-4">
        <FilterBar ... />
      </div>
    )}
  </div>

  {/* 테이블/카드 리스트 */}
  <div className="p-4 pt-0 sm:p-6 sm:pt-0">
    {/* DataTable / Mobile Cards */}
  </div>
</div>
```

### 4. 테이블 행 개선

```tsx
// DataTable hover 스타일 개선
// 현재: hover:bg-primary/5
// 변경:
<tr className="group transition-all hover:bg-accent/50 cursor-pointer
               border-b border-border/30 last:border-0">
  {/* 첫 번째 셀에 좌측 인디케이터 */}
  <td className="relative pl-4">
    <span className="absolute left-0 top-2 bottom-2 w-0.5 rounded-full
                     bg-primary opacity-0 group-hover:opacity-100 transition-opacity" />
    {/* 셀 내용 */}
  </td>
</tr>
```

### 5. 모바일 카드 리디자인

```tsx
<button className="w-full rounded-xl border border-border/50 bg-card
                   p-4 text-left transition-all
                   hover:shadow-brand active:scale-[0.98]">
  {/* 상단: 상태 + 시간 */}
  <div className="flex items-center justify-between mb-2">
    <Badge variant={getStatusBadgeVariant(item.status)}>
      {labelInquiryStatus(item.status)}
    </Badge>
    <span className="text-xs text-muted-foreground tabular-nums">
      {new Date(item.createdAt).toLocaleDateString("ko-KR")}
    </span>
  </div>

  {/* 질문 */}
  <p className="text-sm font-medium leading-snug line-clamp-2 mb-2">
    {item.question}
  </p>

  {/* 하단 메타 */}
  <div className="flex items-center gap-3 text-xs text-muted-foreground">
    <span className="flex items-center gap-1">
      {/* 채널 아이콘 */}
      {labelChannel(item.customerChannel)}
    </span>
    {item.latestAnswerStatus && (
      <Badge variant={getAnswerStatusBadgeVariant(item.latestAnswerStatus)} className="text-[0.65rem]">
        {labelAnswerStatus(item.latestAnswerStatus)}
      </Badge>
    )}
    <span className="ml-auto">{item.documentCount}건</span>
  </div>
</button>
```

### 6. Pagination 개선

```tsx
{/* 현재: 숫자 버튼 나열 */}
{/* 변경: 간결한 인라인 + 이전/다음 */}
<div className="flex items-center justify-between pt-4 border-t border-border/30">
  <p className="text-xs text-muted-foreground">
    {response.totalElements}건 중 {page * size + 1}-{Math.min((page + 1) * size, response.totalElements)}
  </p>
  <div className="flex items-center gap-1">
    <Button variant="ghost" size="sm" disabled={page === 0} onClick={() => handlePageChange(page - 1)}>
      이전
    </Button>
    <span className="px-3 text-sm tabular-nums">
      {page + 1} / {response.totalPages}
    </span>
    <Button variant="ghost" size="sm" disabled={page >= response.totalPages - 1} onClick={() => handlePageChange(page + 1)}>
      다음
    </Button>
  </div>
</div>
```

### 7. 빈 상태 개선

```tsx
<EmptyState
  icon={/* 노트 + 돋보기 일러스트 */}
  title="등록된 문의가 없습니다"
  description="새 문의를 등록하면 RAG 파이프라인이 자동으로 답변 초안을 생성합니다."
  action={{
    label: "첫 문의 등록하기",
    onClick: () => router.push("/inquiries/new"),
  }}
/>
```

---

## 모바일 FAB (Floating Action Button)

문의 작성 버튼이 데스크톱에서만 보이므로, 모바일 FAB 추가:

```tsx
{/* Mobile FAB */}
<button
  onClick={() => router.push("/inquiries/new")}
  className="fixed bottom-20 right-4 z-40 flex h-14 w-14 items-center justify-center
             rounded-full bg-primary text-primary-foreground shadow-lg
             transition-transform hover:scale-105 active:scale-95 md:hidden"
  style={{ marginBottom: "env(safe-area-inset-bottom)" }}
  aria-label="문의 작성"
>
  <PencilPlusIcon className="h-6 w-6" />
</button>
```

---

## Before / After 요약

| 요소 | Before | After |
|------|--------|-------|
| 헤더 | `text-xl` 단순 | `text-2xl~3xl` + 건수 표시 |
| 필터 | 5개 항목 1줄 | 칩 그룹 + 접기/펼치기 |
| 테이블 행 | `hover:bg-primary/5` | 좌측 인디케이터 + hover 애니메이션 |
| 모바일 카드 | 정보 나열 | 구조화 (상태-질문-메타 분리) |
| Pagination | 숫자 버튼 | 간결한 이전/다음 |
| 빈 상태 | 텍스트만 | 일러스트 + 가이드 |
| FAB | 없음 | 모바일 우측 하단 |
