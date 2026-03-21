# 07. 지식 기반 페이지 현대화

**경로:** `/knowledge-base`
**파일:** `src/app/knowledge-base/page.tsx`

---

## 현재 상태

### 레이아웃
```
[지식 기반 관리]                [일괄 인덱싱] [문서 등록]

[전체 문서: N건] [인덱싱 완료: N건] [총 청크: N개]   ← 통계 카드

[제품군별 분포]                                     ← 조건부 표시
[제품A: N건] [제품B: N건] ...

┌────────────────────────────────────────────────┐
│ FilterBar (카테고리, 제품군, 상태, 검색어)         │
│────────────────────────────────────────────────│
│ DataTable / Mobile Cards                       │
│────────────────────────────────────────────────│
│ Pagination                                     │
└────────────────────────────────────────────────┘

[Detail Modal — 인라인 구현]
[Mobile FAB — + 버튼]
```

### 강점
- 인덱싱 상태 폴링 (5초 주기)
- 모바일 FAB 업로드 버튼
- SmartUploadModal 별도 컴포넌트
- INDEXING 상태에 스피너 표시

### 문제점

1. **통계 카드 + 제품군 분포**: 정보가 많으나 시각적 위계 부족
2. **Detail Modal**: `div` + `onClick stopPropagation`으로 수동 구현 — Dialog 미사용
3. **제품군 분포**: 5열 그리드 → 모바일에서 2열이지만 카드가 너무 작음
4. **인덱싱 스피너**: Badge 내 `animate-spin` — 눈에 잘 안 띔
5. **삭제 확인**: `window.confirm()` — 네이티브 다이얼로그
6. **문서 상세**: 텍스트 나열 `<b>라벨:</b> 값` — 구조화 부족

---

## 현대화 설계

### 1. 페이지 헤더 + 통계 통합

```
변경 후:

┌───────────────────────────────────────────────┐
│ 지식 기반 관리                                  │
│ 총 127건의 문서, 98.4% 인덱싱 완료                │
│                                               │
│ [────────────────────────── 98.4% ──]          │  ← 프로그레스 바
│                                               │
│              [일괄 인덱싱]  [문서 등록]           │
└───────────────────────────────────────────────┘
```

```tsx
<div className="space-y-4">
  <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
    <div>
      <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">
        지식 기반 관리
      </h2>
      {stats && (
        <p className="text-sm text-muted-foreground mt-1">
          총 {stats.totalDocuments}건의 문서,{" "}
          <span className="text-[hsl(var(--success))] font-medium">
            {((stats.indexedDocuments / Math.max(stats.totalDocuments, 1)) * 100).toFixed(1)}%
          </span>{" "}
          인덱싱 완료
        </p>
      )}
    </div>
    <div className="flex items-center gap-3">
      <Button variant="outline" onClick={handleIndexAll}>일괄 인덱싱</Button>
      <Button onClick={() => setShowUploadModal(true)}>문서 등록</Button>
    </div>
  </div>

  {/* 인덱싱 프로그레스 바 */}
  {stats && (
    <div className="h-2 rounded-full bg-muted overflow-hidden">
      <div
        className="h-full rounded-full bg-[hsl(var(--success))] transition-all duration-500"
        style={{ width: `${(stats.indexedDocuments / Math.max(stats.totalDocuments, 1)) * 100}%` }}
      />
    </div>
  )}
</div>
```

### 2. 통계 카드 — 컴팩트 Metric Strip

통계 카드를 3개 별도 카드에서 **1줄 Metric Strip**으로 변경:

```tsx
{stats && (
  <div className="flex items-center gap-6 rounded-xl border bg-card px-6 py-4
                  overflow-x-auto scrollbar-none">
    <MetricItem label="전체 문서" value={`${stats.totalDocuments}건`} />
    <div className="h-8 w-px bg-border shrink-0" />
    <MetricItem label="인덱싱 완료" value={`${stats.indexedDocuments}건`} accent="success" />
    <div className="h-8 w-px bg-border shrink-0" />
    <MetricItem label="총 청크" value={`${stats.totalChunks.toLocaleString()}개`} />
  </div>
)}

function MetricItem({ label, value, accent }: { label: string; value: string; accent?: string }) {
  return (
    <div className="shrink-0">
      <p className="text-[0.65rem] font-medium uppercase tracking-wide text-muted-foreground">
        {label}
      </p>
      <p className={cn(
        "text-lg font-bold tracking-tight",
        accent === "success" && "text-[hsl(var(--success))]"
      )}>
        {value}
      </p>
    </div>
  );
}
```

### 3. 제품군 분포 — 비율 시각화

단순 숫자 대신 비율 바 추가:

```tsx
{stats.byProductFamily && (
  <section className="space-y-3">
    <h3 className="text-sm font-medium text-muted-foreground">제품군별 분포</h3>
    <div className="space-y-2">
      {Object.entries(stats.byProductFamily)
        .sort(([, a], [, b]) => b - a)
        .map(([family, count]) => {
          const percentage = (count / stats.totalDocuments) * 100;
          return (
            <div key={family} className="flex items-center gap-3">
              <span className="text-xs font-medium w-24 truncate text-right">
                {labelProductFamily(family)}
              </span>
              <div className="flex-1 h-2.5 rounded-full bg-muted overflow-hidden">
                <div
                  className="h-full rounded-full bg-primary/60 transition-all duration-300"
                  style={{ width: `${percentage}%` }}
                />
              </div>
              <span className="text-xs tabular-nums text-muted-foreground w-12 text-right">
                {count}건
              </span>
            </div>
          );
        })}
    </div>
  </section>
)}
```

### 4. 인덱싱 상태 — 시각적 강화

```tsx
// 현재: Badge 내 작은 스피너
// 변경: 테이블 행 전체에 시각적 힌트

<tr className={cn(
  "transition-all",
  (item.status === "INDEXING" || item.status === "REINDEXING")
    && "bg-warning/5 animate-pulse-subtle"
)}>
```

인덱싱 중인 문서 카드(모바일):
```tsx
<button className={cn(
  "w-full rounded-xl border bg-card p-4 text-left transition-all",
  (item.status === "INDEXING" || item.status === "REINDEXING")
    ? "border-[hsl(var(--warning))]/30 bg-[hsl(var(--warning-light))]"
    : "border-border/50 hover:shadow-brand"
)}>
```

### 5. Detail Modal → Dialog 패턴

```tsx
{/* 현재: div overlay 수동 구현 */}
{/* 변경: 체계적 Dialog + 정보 구조화 */}

<div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center
                bg-[hsl(var(--overlay))/60] backdrop-blur-sm
                animate-in fade-in duration-200"
     onClick={() => setShowDetailModal(false)}>
  <div className="w-full max-w-2xl rounded-t-2xl sm:rounded-2xl border bg-card
                  shadow-2xl animate-in slide-in-from-bottom-4 sm:slide-in-from-bottom-2
                  duration-300 max-h-[85vh] overflow-y-auto"
       onClick={(e) => e.stopPropagation()}>

    {/* 헤더 */}
    <div className="sticky top-0 flex items-center justify-between
                    border-b bg-card/90 backdrop-blur px-6 py-4 rounded-t-2xl">
      <h3 className="text-lg font-semibold truncate pr-4">{selectedDoc.title}</h3>
      <Button variant="ghost" size="sm" onClick={() => setShowDetailModal(false)}>
        <XIcon className="h-4 w-4" />
      </Button>
    </div>

    {/* 메타 정보 — 그리드 */}
    <div className="p-6 space-y-4">
      <div className="grid grid-cols-2 gap-4">
        <DetailItem label="카테고리" value={labelKbCategory(selectedDoc.category)} />
        <DetailItem label="제품군" value={
          selectedDoc.productFamily
            ? <Badge variant="info">{labelProductFamily(selectedDoc.productFamily)}</Badge>
            : "-"
        } />
        <DetailItem label="파일" value={`${selectedDoc.fileName} (${formatSize(selectedDoc.fileSize)})`} />
        <DetailItem label="상태" value={
          <Badge variant={getStatusBadgeVariant(selectedDoc.status)}>
            {labelDocStatus(selectedDoc.status)}
          </Badge>
        } />
        <DetailItem label="청크 / 벡터" value={`${selectedDoc.chunkCount ?? "-"} / ${selectedDoc.vectorCount ?? "-"}`} />
        <DetailItem label="등록일" value={formatDate(selectedDoc.createdAt)} />
      </div>

      {selectedDoc.description && (
        <div>
          <p className="text-xs font-medium text-muted-foreground mb-1">설명</p>
          <p className="text-sm">{selectedDoc.description}</p>
        </div>
      )}

      {selectedDoc.lastError && (
        <div className="rounded-lg border border-destructive/30 bg-destructive/5 p-4">
          <p className="text-xs font-medium text-destructive mb-1">오류</p>
          <p className="text-sm text-destructive">{selectedDoc.lastError}</p>
        </div>
      )}
    </div>

    {/* 액션 바 */}
    <div className="sticky bottom-0 flex items-center justify-end gap-3
                    border-t bg-card/90 backdrop-blur px-6 py-4">
      <Button variant="outline" onClick={() => handleIndex(selectedDoc.documentId)}
              disabled={selectedDoc.status === "INDEXING"}>
        {selectedDoc.status === "INDEXED" ? "재인덱싱" : "인덱싱"}
      </Button>
      <Button variant="destructive" onClick={() => handleDelete(selectedDoc)}>
        삭제
      </Button>
    </div>
  </div>
</div>
```

**모바일 개선:**
- `items-end` → 바텀 시트 형태 (모바일)
- `rounded-t-2xl` → 하단에서 슬라이드 업
- `max-h-[85vh]` → 화면 대부분 차지하되 상단 여백
- `slide-in-from-bottom-4` → 자연스러운 진입 애니메이션

### 6. Mobile FAB 위치 조정

```tsx
{/* 현재: bottom-20 right-4 */}
{/* Bottom Nav 위로, 오른쪽 정렬 */}
<button
  className="fixed z-40 flex h-14 w-14 items-center justify-center
             rounded-full bg-primary text-primary-foreground
             shadow-lg shadow-primary/25
             transition-all hover:scale-105 active:scale-95 md:hidden
             bottom-[calc(4rem+env(safe-area-inset-bottom)+0.5rem)] right-4"
  aria-label="문서 등록"
>
```

---

## 모바일 레이아웃 요약

| 요소 | 데스크톱 | 모바일 |
|------|---------|--------|
| 통계 | Metric Strip (가로) | Metric Strip (스크롤) |
| 제품군 분포 | 비율 바 + 레이블 | 비율 바 (레이블 축소) |
| 문서 테이블 | DataTable | 카드 리스트 |
| 상세 모달 | 중앙 Dialog | 바텀 시트 |
| FAB | 없음 (header 버튼) | 우측 하단 |
| 버튼 | 나란히 배치 | 풀 와이드 스택 |

---

## window.confirm 대체

```tsx
// 현재: window.confirm("삭제하시겠습니까?")
// 변경: 커스텀 확인 모달

const [confirmAction, setConfirmAction] = useState<{
  title: string;
  message: string;
  onConfirm: () => void;
  variant?: "default" | "destructive";
} | null>(null);

// 사용
handleDelete = (doc) => {
  setConfirmAction({
    title: "문서 삭제",
    message: `"${doc.title}" 문서를 삭제하시겠습니까?\n관련 벡터 데이터도 함께 삭제됩니다.`,
    onConfirm: () => deleteAndRefresh(doc),
    variant: "destructive",
  });
};
```
