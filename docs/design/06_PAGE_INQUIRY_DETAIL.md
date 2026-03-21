# 06. 문의 상세 페이지 현대화

**경로:** `/inquiries/[id]`
**파일:** `src/app/inquiries/[id]/InquiryDetailClient.tsx` + 탭 컴포넌트들

---

## 현재 상태

### 레이아웃
```
[← 목록으로] [문의 상세 #abc12345]

┌─────────────────────────────────────────┐
│ [문의 정보] │ [답변] │ [이력]  ← Tabs    │
│─────────────────────────────────────────│
│                                         │
│ (탭 콘텐츠 영역)                         │
│                                         │
└─────────────────────────────────────────┘
```

**이 페이지는 프로젝트에서 가장 복잡하고 가장 중요한 페이지입니다.**

### 탭별 현재 구조

#### 문의 정보 탭 (InquiryInfoTab)
- 문의 질문 표시/편집
- 첨부 문서 목록 + 인덱싱 상태
- PDF 뷰어 (모달 확장)
- 문의 메타데이터

#### 답변 탭 (InquiryAnswerTab) ★ 가장 복잡
- Pipeline Progress (픽셀 강아지 애니메이션 + 6단계 진행)
- SSE 스트리밍 답변 생성
- AnswerEditor (Tiptap WYSIWYG)
- WorkflowResultCard (분석 결과)
- 검토 → 승인 → 발송 워크플로
- PDF 근거 문서 뷰어

#### 이력 탭 (InquiryHistoryTab)
- 리비전 타임라인

### 문제점

1. **탭 전환 시 상태 유실**: `Tabs` 컴포넌트가 unmount/remount
2. **답변 탭 정보 밀도**: 파이프라인 + 에디터 + 결과 + 워크플로가 세로 나열
3. **뒤로가기 UX**: `← 목록으로` 버튼이 텍스트 — 시각적 약함
4. **탭 스타일**: border-bottom 방식 — 모바일에서 탭 레이블 넘침 가능
5. **문의 ID 표시**: `#abc12345` 잘린 ID — 복사 기능 없음
6. **에디터 도구모음**: 작은 아이콘 버튼 밀집 — 모바일 터치 어려움

---

## 현대화 설계

### 1. 페이지 헤더 개선

```tsx
<div className="flex items-center gap-3">
  <Button
    variant="ghost"
    size="sm"
    onClick={() => router.push("/inquiries")}
    className="rounded-lg"
  >
    <ArrowLeftIcon className="h-4 w-4 mr-1" />
    목록
  </Button>

  <div className="h-6 w-px bg-border" aria-hidden="true" />

  <div className="flex-1 min-w-0">
    <div className="flex items-center gap-2">
      <h2 className="text-xl sm:text-2xl font-bold tracking-tight truncate">
        문의 상세
      </h2>
      <button
        onClick={() => navigator.clipboard.writeText(inquiryId)}
        className="shrink-0 rounded-md bg-muted px-2 py-0.5
                   text-xs font-mono text-muted-foreground
                   hover:bg-primary/10 hover:text-primary transition-colors"
        title="ID 복사"
      >
        #{inquiryId.slice(0, 8)}
      </button>
    </div>
    <p className="text-xs text-muted-foreground mt-0.5 truncate">
      {inquiry?.question?.slice(0, 60)}...
    </p>
  </div>
</div>
```

### 2. 탭 스타일 현대화

```tsx
// 현재: border-bottom 언더라인
// 변경: Pill 형태 세그먼트 컨트롤

<div className="flex items-center gap-1 rounded-xl bg-muted/50 p-1">
  {tabs.map((tab) => (
    <button
      key={tab.key}
      onClick={() => setActiveTab(tab.key)}
      className={cn(
        "flex-1 rounded-lg px-4 py-2 text-sm font-medium transition-all",
        activeTab === tab.key
          ? "bg-card text-foreground shadow-sm"
          : "text-muted-foreground hover:text-foreground"
      )}
    >
      {tab.label}
    </button>
  ))}
</div>
```

### 3. 문의 정보 탭 개선

```
현재: 세로 나열 (질문, 문서 목록, 메타)
변경: 2열 분할 (데스크톱)

Desktop:
┌──────────────────┬──────────────────┐
│ 문의 질문         │ 첨부 문서         │
│ (편집 가능)       │ (인덱싱 상태)      │
│                  │ [PDF 뷰어]        │
├──────────────────┴──────────────────┤
│ 메타 정보 (채널, 제품군, 등록일 등)    │
└─────────────────────────────────────┘

Mobile: 세로 나열 (현재와 동일)
```

```tsx
<div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
  {/* 좌측: 질문 */}
  <div className="space-y-4">
    <div className="rounded-xl border bg-muted/20 p-4">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">
          문의 내용
        </h3>
        <Button variant="ghost" size="sm" onClick={toggleEdit}>
          {editing ? "취소" : "편집"}
        </Button>
      </div>
      {editing ? (
        <textarea className="w-full rounded-lg border p-3 text-sm min-h-[120px]" />
      ) : (
        <p className="text-sm leading-relaxed whitespace-pre-wrap">
          {inquiry.question}
        </p>
      )}
    </div>

    {/* 메타 정보 */}
    <div className="grid grid-cols-2 gap-3">
      <MetaItem label="채널" value={labelChannel(inquiry.customerChannel)} />
      <MetaItem label="등록일" value={formatDate(inquiry.createdAt)} />
      <MetaItem label="답변 톤" value={labelTone(inquiry.preferredTone)} />
      <MetaItem label="상태" value={<Badge>{labelInquiryStatus(inquiry.status)}</Badge>} />
    </div>
  </div>

  {/* 우측: 문서 */}
  <div className="space-y-3">
    <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">
      첨부 문서
    </h3>
    {/* 문서 카드 리스트 */}
  </div>
</div>
```

### 4. 답변 탭 — 핵심 UX 개선

#### 4a. Pipeline Progress 유지

현재 픽셀 강아지 + 6단계 애니메이션은 **프로젝트의 시그니처 UI**이므로 기본 구조 유지.

**미세 조정:**
```tsx
// 래퍼 카드에 시각적 강조 추가
<div className="rounded-2xl border-2 border-primary/20 bg-primary/[0.02] p-6
                animate-in fade-in duration-300">
  <PipelineProgress ... />
</div>
```

#### 4b. 스트리밍 답변 영역 개선

```tsx
{/* 스트리밍 중 답변 표시 */}
<div className="rounded-2xl border bg-card p-6 shadow-sm">
  <div className="flex items-center justify-between mb-4">
    <h3 className="text-lg font-semibold">답변 초안</h3>
    <div className="flex items-center gap-2">
      {isStreaming && (
        <span className="flex items-center gap-1.5 text-xs text-primary">
          <span className="h-2 w-2 rounded-full bg-primary animate-pulse" />
          생성 중... ({streamingTokenCount}토큰)
        </span>
      )}
    </div>
  </div>

  {/* 에디터 */}
  <div className="rounded-xl border bg-background">
    <AnswerEditor ... />
  </div>
</div>
```

#### 4c. 워크플로 액션 버튼 배치

```
현재: 버튼이 에디터 아래 가로 나열
변경: 상태에 따른 단계별 표시

[답변 생성] → [검토 요청] → [승인] → [발송]

각 단계에서 현재 액션만 Primary로, 나머지는 disabled
```

```tsx
<div className="flex items-center gap-3 rounded-xl border bg-muted/30 p-4">
  {/* 진행 표시 */}
  <div className="flex items-center gap-2 flex-1">
    {workflowSteps.map((step, i) => (
      <Fragment key={step.key}>
        <span className={cn(
          "flex h-7 w-7 items-center justify-center rounded-full text-xs font-bold",
          step.completed && "bg-success/20 text-[hsl(var(--success))]",
          step.active && "bg-primary text-primary-foreground",
          !step.completed && !step.active && "bg-muted text-muted-foreground"
        )}>
          {step.completed ? "✓" : i + 1}
        </span>
        {i < workflowSteps.length - 1 && (
          <div className={cn(
            "h-px flex-1",
            step.completed ? "bg-success/40" : "bg-border"
          )} />
        )}
      </Fragment>
    ))}
  </div>

  {/* 현재 액션 버튼 */}
  <Button className="shrink-0" onClick={currentAction.handler}>
    {currentAction.label}
  </Button>
</div>
```

#### 4d. WorkflowResultCard 개선

```tsx
{/* 분석 결과 — 접기/펼치기 */}
<details className="group rounded-xl border bg-card shadow-sm">
  <summary className="flex items-center justify-between cursor-pointer
                      px-6 py-4 text-sm font-semibold">
    분석 결과
    <ChevronDown className="h-4 w-4 transition-transform group-open:rotate-180" />
  </summary>
  <div className="px-6 pb-6 pt-2 border-t">
    <WorkflowResultCard ... />
  </div>
</details>
```

### 5. 이력 탭 — 타임라인 시각화

```tsx
{/* 리비전 타임라인 */}
<div className="relative space-y-0">
  {/* 수직 연결선 */}
  <div className="absolute left-4 top-4 bottom-4 w-px bg-border" aria-hidden="true" />

  {revisions.map((rev, i) => (
    <div key={rev.id} className="relative flex gap-4 pb-6 last:pb-0">
      {/* 타임라인 도트 */}
      <div className={cn(
        "relative z-10 flex h-8 w-8 shrink-0 items-center justify-center rounded-full",
        i === 0
          ? "bg-primary text-primary-foreground"
          : "bg-muted text-muted-foreground"
      )}>
        <span className="text-xs font-bold">v{rev.version}</span>
      </div>

      {/* 리비전 내용 */}
      <div className="flex-1 rounded-xl border bg-card p-4">
        <div className="flex items-center justify-between">
          <span className="text-sm font-semibold">{rev.action}</span>
          <span className="text-xs text-muted-foreground">
            {formatRelativeTime(rev.createdAt)}
          </span>
        </div>
        {rev.changes && (
          <p className="text-sm text-muted-foreground mt-2 line-clamp-3">
            {rev.changes}
          </p>
        )}
      </div>
    </div>
  ))}
</div>
```

---

## AnswerEditor 모바일 최적화

### 도구모음 개선

```tsx
{/* 현재: 모든 버튼 한 줄 — 모바일에서 넘침 */}
{/* 변경: 모바일에서 스크롤 가능한 도구모음 */}
<div className="flex items-center gap-0.5 overflow-x-auto border-b
                px-2 py-1 scrollbar-none">
  {/* 서식 그룹 */}
  <div className="flex items-center gap-0.5 shrink-0">
    <ToolButton icon={Bold} ... />
    <ToolButton icon={Italic} ... />
  </div>
  <div className="h-4 w-px bg-border mx-1 shrink-0" />
  {/* 헤딩 그룹 */}
  <div className="flex items-center gap-0.5 shrink-0">
    <ToolButton icon={Heading2} ... />
    <ToolButton icon={Heading3} ... />
  </div>
  <div className="h-4 w-px bg-border mx-1 shrink-0" />
  {/* 리스트 그룹 */}
  <div className="flex items-center gap-0.5 shrink-0">
    <ToolButton icon={List} ... />
    <ToolButton icon={ListOrdered} ... />
    <ToolButton icon={Quote} ... />
  </div>
  <div className="h-4 w-px bg-border mx-1 shrink-0" />
  {/* 미디어 그룹 */}
  <div className="flex items-center gap-0.5 shrink-0">
    <ToolButton icon={Link2} ... />
    <ToolButton icon={ImagePlus} ... />
    <ToolButton icon={Mail} label="Gmail 복사" ... />
  </div>
</div>
```

---

## 모바일 레이아웃

| 요소 | 데스크톱 | 모바일 |
|------|---------|--------|
| 탭 | Pill 세그먼트 | Pill (동일, 터치 최적화) |
| 정보 탭 | 2열 분할 | 1열 종배치 |
| 파이프라인 | 가로 스텝 | 가로 스텝 (축소 표시) |
| 에디터 도구 | 그룹 구분 | 가로 스크롤 |
| 워크플로 바 | 가로 | 가로 (축소) |
| PDF 뷰어 | 인라인 | 풀스크린 모달 (기존 유지) |

---

## 성능 고려

- `AnswerEditor`와 `PdfViewer`는 이미 `dynamic(() => import(...), { ssr: false })` 적용 → 양호
- 탭 전환 시 unmount 방지: `display: none` + `display: block` 패턴 고려
  - 특히 에디터 상태 보존을 위해 중요
- SSE 연결은 답변 탭이 비활성이어도 유지 (현재 구현 양호)
