# 03. 대시보드 페이지 현대화

**경로:** `/dashboard`
**파일:** `src/app/dashboard/page.tsx`

---

## 현재 상태

### 레이아웃 구조
```
[Header + PeriodSelector + CSV 버튼]
[MetricCards — 6열 그리드]
[TimelineChart (2fr) | StatusPieChart (1fr)]
[KB 상위 참조 문서]
[RAG 파이프라인 성능]
[최근 문의 테이블 / 모바일 카드]
[최근 실패 사유]
```

### 문제점

1. **메트릭 카드 6열**: `lg:grid-cols-6`은 카드가 너무 좁아 텍스트 넘침 가능
2. **모든 섹션 동일 패턴**: Card + CardContent + h3 + 콘텐츠 반복
3. **인라인 카드 vs Card 컴포넌트 혼용**: 메트릭은 `<article>`, 차트는 `<Card>`
4. **RAG 메트릭 스타일 불일치**: `style={{ color: "var(--color-text-secondary, ...)" }}` 존재하지 않는 변수
5. **숫자 애니메이션 없음**: 메트릭 값이 정적으로 렌더링
6. **모바일 메트릭 카드**: `grid-cols-1`로 세로 나열 → 공간 낭비

---

## 현대화 설계

### 1. 페이지 헤더 강화

```
현재: [운영 대시보드]                    [PeriodSelector] [CSV]
변경: [운영 대시보드]                    [PeriodSelector] [CSV]
      [마지막 업데이트: 2분 전]
```

```tsx
<div className="flex flex-col gap-1 sm:flex-row sm:items-end sm:justify-between">
  <div>
    <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">
      운영 대시보드
    </h2>
    <p className="text-sm text-muted-foreground mt-1">
      실시간 CS 대응 현황을 한눈에 확인하세요
    </p>
  </div>
  <div className="flex items-center gap-3">
    <PeriodSelector value={period} onChange={setPeriod} />
    <Button variant="outline" size="sm" onClick={handleExportCsv}>
      CSV 내보내기
    </Button>
  </div>
</div>
```

### 2. 메트릭 카드 — Bento Grid 변형

**현재:** 6개 동일 크기 → 좁은 카드
**변경:** 중요도에 따른 비대칭 그리드

```
Desktop (lg):
┌───────────┬───────────┬───────────┐
│  발송 성공률  │  중복 차단률  │  Fallback  │  ← 주요 3개 (1fr씩)
│   87.5%    │   12.3%   │   비율     │
│  42/48건   │   6/49건   │  5.2%    │
├─────┬──────┼─────┬─────┼─────┬─────┤
│평균처리│완료건수 │KB 활용률│          │  ← 보조 3개 (1fr씩)
│ 시간  │      │       │          │
└─────┴──────┴─────┴─────┴─────┴─────┘

Mobile:
┌──────────┬──────────┐
│ 발송 성공률 │ 중복 차단률 │  ← 2열
├──────────┼──────────┤
│ Fallback │ 평균처리시간│  ← 2열
├──────────┴──────────┤
│ 완료 건수  │ KB 활용률  │  ← 2열
└──────────┴──────────┘
```

```tsx
<section className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-3">
  {metricCards.map((metric) => (
    <article
      key={metric.label}
      className="group relative overflow-hidden rounded-2xl
                 border border-border/50 bg-card p-4 sm:p-5
                 shadow-brand transition-all hover:shadow-brand-lg
                 hover:-translate-y-0.5"
    >
      {/* 미세한 그라데이션 오버레이 */}
      <div className="absolute inset-0 bg-gradient-to-br from-primary/[0.02] to-transparent
                      opacity-0 group-hover:opacity-100 transition-opacity" />
      <p className="relative text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {metric.label}
      </p>
      <p className="relative text-2xl sm:text-3xl font-bold tracking-tight text-foreground mt-1">
        {metric.value}
      </p>
      {metric.subValue && (
        <p className="relative text-xs text-muted-foreground mt-0.5">
          {metric.subValue}
        </p>
      )}
    </article>
  ))}
</section>
```

### 3. 차트 영역 — 비대칭 레이아웃 유지 + 카드 개선

```tsx
<div className="grid grid-cols-1 gap-4 lg:grid-cols-[2fr_1fr]">
  {/* Timeline — 넓은 영역 */}
  <Card className="overflow-hidden">
    <CardContent className="p-0">
      <div className="flex items-center justify-between px-6 pt-6 pb-2">
        <h3 className="text-lg font-semibold">문의 처리 현황</h3>
        {/* 미니 범례 */}
        <div className="flex items-center gap-4 text-xs text-muted-foreground">
          <span className="flex items-center gap-1.5">
            <span className="h-2 w-2 rounded-full bg-primary" />접수
          </span>
          <span className="flex items-center gap-1.5">
            <span className="h-2 w-2 rounded-full bg-success" />완료
          </span>
        </div>
      </div>
      <div className="px-2 pb-4">
        <TimelineChart data={timeline?.data ?? []} />
      </div>
    </CardContent>
  </Card>

  {/* Pie Chart */}
  <Card>
    <CardContent className="p-6">
      <h3 className="text-lg font-semibold mb-4">상태별 분포</h3>
      <StatusPieChart inquiries={inquiryListResponse} />
    </CardContent>
  </Card>
</div>
```

### 4. KB 상위 참조 문서 — 순위 시각화 강화

```tsx
{kbUsage.topDocuments.map((doc, idx) => (
  <div
    key={doc.documentId}
    className="flex items-center gap-4 rounded-xl border border-border/50
               bg-card p-4 transition-colors hover:bg-accent/50"
  >
    {/* 순위 뱃지 — 상위 3개 강조 */}
    <span className={cn(
      "flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-sm font-bold",
      idx < 3
        ? "bg-primary/10 text-primary"
        : "bg-muted text-muted-foreground"
    )}>
      {idx + 1}
    </span>

    {/* 파일명 */}
    <span className="flex-1 text-sm font-medium truncate">
      {doc.fileName}
    </span>

    {/* 참조 카운트 — 바 차트 힌트 */}
    <div className="flex items-center gap-2">
      <div className="hidden sm:block h-1.5 w-20 rounded-full bg-muted overflow-hidden">
        <div
          className="h-full rounded-full bg-primary/40"
          style={{ width: `${Math.min(100, (doc.referenceCount / Math.max(...kbUsage.topDocuments.map(d => d.referenceCount))) * 100)}%` }}
        />
      </div>
      <span className="text-sm tabular-nums text-muted-foreground">
        {doc.referenceCount}회
      </span>
    </div>
  </div>
))}
```

### 5. RAG 파이프라인 성능 — 컬러 코딩

```tsx
const ragCards = [
  { label: "검색 정확도", value: "...", color: "primary" },
  { label: "리랭킹 개선", value: "...", color: "success" },
  { label: "Critic 수정률", value: "...", color: "warning" },
  // ...
];

{ragCards.map((card) => (
  <article className="rounded-xl border border-border/50 bg-card p-4">
    <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
      {card.label}
    </p>
    <p className={cn(
      "mt-1 text-xl font-bold tracking-tight",
      card.color === "success" && "text-[hsl(var(--success))]",
      card.color === "warning" && "text-[hsl(var(--warning))]",
      // default: text-foreground
    )}>
      {card.value}
    </p>
  </article>
))}
```

### 6. 최근 문의 — 모바일 카드 개선

```tsx
{/* 모바일 카드 */}
<button className="w-full rounded-xl border border-border/50 bg-card p-4
                   text-left transition-all hover:bg-accent/50
                   hover:shadow-brand active:scale-[0.99]">
  <div className="flex items-start justify-between gap-3">
    <p className="text-sm font-medium leading-snug line-clamp-2 flex-1">
      {item.question}
    </p>
    <Badge variant={getStatusVariant(item.status)} className="shrink-0">
      {labelInquiryStatus(item.status)}
    </Badge>
  </div>
  <div className="mt-2 flex items-center gap-2 text-xs text-muted-foreground">
    <span>{item.createdAt.slice(0, 10)}</span>
    <span className="h-1 w-1 rounded-full bg-border" />
    <span>{labelChannel(item.customerChannel)}</span>
  </div>
</button>
```

### 7. 실패 사유 — 시각적 개선

```tsx
{/* 현재: ul > li 텍스트 목록 */}
{/* 변경: 카드 형태 + 횟수 강조 */}
{metrics?.topFailureReasons.map((item, idx) => (
  <div key={idx} className="flex items-center justify-between rounded-lg
                             border border-border/30 bg-destructive/5 px-4 py-3">
    <span className="text-sm">{item.reason}</span>
    <Badge variant="danger">{item.count}건</Badge>
  </div>
))}
```

---

## 모바일 레이아웃 요약

| 섹션 | 데스크톱 | 모바일 |
|------|---------|--------|
| 메트릭 | 3열 | 2열 |
| 차트 | 2fr+1fr 횡배치 | 1열 종배치 |
| KB 참조 | 가로 바 차트 포함 | 바 차트 숨김 |
| 문의 목록 | DataTable | 카드 리스트 |
| 실패 사유 | 카드 | 카드 (동일) |

---

## 스켈레톤 로딩 개선

```tsx
{/* 메트릭 스켈레톤 — 2열 (모바일 대응) */}
<section className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-3">
  {[1, 2, 3, 4, 5, 6].map((i) => (
    <article className="rounded-2xl border bg-card p-4 sm:p-5" key={i}>
      <Skeleton className="mb-3 h-3 w-16" />
      <Skeleton className="mb-2 h-8 w-20" />
      <Skeleton className="h-3 w-12" />
    </article>
  ))}
</section>
```
