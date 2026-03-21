# 05. 문의 작성 페이지 현대화

**경로:** `/inquiries/new`
**파일:** `src/components/inquiry/InquiryCreateForm.tsx`

---

## 현재 상태

### 레이아웃
```
┌─────────────────────────────────────┐
│ 새 고객 문의 등록                      │
│ 고객의 기술 문의를 등록하고...           │
│ ─────────────────────────────────── │
│ 문의 내용                            │
│ [질문 textarea]                      │
│ 관련 제품군 (최대 3개) [Select + 추가]  │
│   [tag] [tag]                       │
│ [채널 Select] [답변 톤 Select]        │
│ ─────────────────────────────────── │
│ 파일 첨부                            │
│ [File Drop Zone]                     │
│ [File Preview]                       │
│ ─────────────────────────────────── │
│ [문의 등록 버튼 — full width pill]     │
└─────────────────────────────────────┘
```

### 강점
- Zod + React Hook Form 검증 체계 양호
- 이미지 프리뷰 + 파일 타입 Badge
- Sticky 제출 버튼 (모바일 대응)
- `rounded-full` CTA 버튼

### 문제점
1. **단일 열 나열**: 모든 필드가 위에서 아래로 단순 배치
2. **섹션 구분**: `<hr>` 라인만 — 시각적 영역 분리 약함
3. **제품군 선택 UX**: Select + "추가" 버튼 → 직관적이지 않음
4. **Drop Zone 스타일**: `border-dashed` 기본형 — 모던하지 않음
5. **진행 상태**: 제출 중 `disabled` + "등록 중..." 텍스트만
6. **모바일 키보드**: textarea 포커스 시 뷰포트 줄어듦에 대한 대응 없음

---

## 현대화 설계

### 1. 폼 레이아웃 — 스텝 or 섹션 카드

**선택지 A: Stepper (다단계)**
짧은 폼이므로 굳이 다단계로 나눌 필요 없음 → 기각

**선택지 B: 섹션 카드 분리 (채택)**

```
┌──────────────────────────────────────────┐
│ 새 고객 문의 등록                          │
│ RAG 파이프라인이 자동으로 답변 초안을 생성합니다 │
└──────────────────────────────────────────┘

┌──────────────────────────────────────────┐
│ ① 문의 내용                              │
│                                          │
│ [질문 textarea — 넉넉한 높이]              │
│                                          │
│ 관련 제품군                               │
│ [칩 토글 그리드 — 클릭으로 선택/해제]        │
│                                          │
│ [채널]              [답변 톤]             │
└──────────────────────────────────────────┘

┌──────────────────────────────────────────┐
│ ② 파일 첨부 (선택)                        │
│                                          │
│ ┌─ · · · · · · · · · · · · · · · · ─┐  │
│ │  📄 파일을 여기에 끌어다 놓거나       │  │
│ │     클릭하여 선택하세요              │  │
│ │  PDF, DOC, 이미지 최대 20MB        │  │
│ └─ · · · · · · · · · · · · · · · · ─┘  │
│                                          │
│ [파일 프리뷰 카드]                        │
└──────────────────────────────────────────┘

[──── 문의 등록 ────]  ← Pill CTA
```

### 2. 제품군 선택 — 칩 토글 방식

Select + "추가" 버튼 → 클릭 토글 칩으로 변경:

```tsx
<div className="space-y-2">
  <label className="text-sm font-medium">
    관련 제품군
    <span className="text-xs text-muted-foreground ml-1">
      (최대 3개 선택)
    </span>
  </label>
  <div className="flex flex-wrap gap-2">
    {Object.entries(PRODUCT_FAMILY_LABELS).map(([key, label]) => {
      const selected = selectedProductFamilies.includes(key);
      const disabled = !selected && selectedProductFamilies.length >= 3;
      return (
        <button
          key={key}
          type="button"
          disabled={disabled}
          onClick={() => {
            if (selected) {
              setSelectedProductFamilies(prev => prev.filter(v => v !== key));
            } else {
              setSelectedProductFamilies(prev => [...prev, key]);
            }
          }}
          className={cn(
            "rounded-full border px-3 py-1.5 text-xs font-medium transition-all",
            selected
              ? "border-primary bg-primary/10 text-primary shadow-sm"
              : "border-border bg-card text-muted-foreground hover:border-primary/30",
            disabled && "opacity-40 cursor-not-allowed"
          )}
        >
          {selected && (
            <span className="mr-1">✓</span>
          )}
          {label}
        </button>
      );
    })}
  </div>
</div>
```

### 3. Drop Zone 현대화

```tsx
<label className="group cursor-pointer">
  <div className={cn(
    "relative flex flex-col items-center justify-center gap-3",
    "rounded-2xl border-2 border-dashed p-8",
    "transition-all duration-200",
    isDragging
      ? "border-primary bg-primary/5 scale-[1.02]"
      : "border-border hover:border-primary/40 hover:bg-accent/30"
  )}>
    {/* 업로드 아이콘 */}
    <div className="flex h-12 w-12 items-center justify-center
                    rounded-xl bg-muted group-hover:bg-primary/10
                    transition-colors">
      <UploadIcon className="h-6 w-6 text-muted-foreground
                             group-hover:text-primary transition-colors" />
    </div>

    <div className="text-center">
      <p className="text-sm font-medium">
        파일을 끌어다 놓거나 <span className="text-primary">선택</span>하세요
      </p>
      <p className="text-xs text-muted-foreground mt-1">
        PDF, DOC, DOCX, PNG, JPG, WEBP (최대 20MB)
      </p>
    </div>

    <input type="file" className="absolute inset-0 opacity-0 cursor-pointer" ... />
  </div>
</label>
```

### 4. 파일 프리뷰 카드 개선

```tsx
{file && (
  <div className="flex items-center gap-3 rounded-xl border bg-card p-3
                  shadow-sm animate-in fade-in slide-in-from-bottom-2 duration-200">
    {/* 썸네일 */}
    <div className="flex h-12 w-12 shrink-0 items-center justify-center
                    rounded-lg bg-muted overflow-hidden">
      {isImageFile(file) && imagePreviewUrl ? (
        <img src={imagePreviewUrl} alt={file.name}
             className="h-full w-full object-cover" />
      ) : (
        <DocumentIcon className="h-6 w-6 text-muted-foreground" />
      )}
    </div>

    {/* 파일 정보 */}
    <div className="flex-1 min-w-0">
      <div className="flex items-center gap-2">
        <span className="text-sm font-medium truncate">{file.name}</span>
        <Badge variant={getFileTypeBadge(file).variant} className="shrink-0">
          {getFileTypeBadge(file).label}
        </Badge>
      </div>
      <p className="text-xs text-muted-foreground">
        {formatFileSize(file.size)}
      </p>
    </div>

    {/* 삭제 */}
    <button
      type="button"
      onClick={removeFile}
      className="shrink-0 rounded-lg p-1.5 text-muted-foreground
                 hover:text-destructive hover:bg-destructive/10 transition-colors"
      aria-label="파일 제거"
    >
      <XIcon className="h-4 w-4" />
    </button>
  </div>
)}
```

### 5. 제출 버튼 — 로딩 상태 개선

```tsx
<div className="sticky bottom-20 z-10 -mx-4 bg-gradient-to-t from-card via-card
                to-card/0 px-4 pt-6 pb-3 sm:static sm:mx-0 sm:bg-none sm:px-0
                sm:pt-0 sm:pb-0 md:bottom-0">
  <Button
    size="lg"
    className="w-full rounded-full text-base font-semibold
               shadow-lg shadow-primary/20 hover:shadow-xl hover:shadow-primary/30
               hover:scale-[1.01] active:scale-[0.99] transition-all"
    type="submit"
    disabled={isSubmitting}
  >
    {isSubmitting ? (
      <span className="flex items-center gap-2">
        <Loader2 className="h-4 w-4 animate-spin" />
        등록 중...
      </span>
    ) : (
      "문의 등록"
    )}
  </Button>
</div>
```

**Sticky 영역 개선:**
- `bg-gradient-to-t from-card via-card to-card/0` → 자연스러운 페이드

### 6. Textarea 개선

```tsx
<textarea
  className="w-full rounded-xl border border-input bg-transparent
             px-4 py-3 text-sm shadow-sm leading-relaxed
             placeholder:text-muted-foreground
             focus-visible:outline-none focus-visible:ring-2
             focus-visible:ring-ring/50 focus-visible:border-primary
             min-h-[160px] resize-y transition-shadow"
  rows={6}
  placeholder="고객의 기술 문의 내용을 상세히 입력하세요.&#10;&#10;예: CFX96 PCR 장비에서 Baseline 보정이 실패하며 'Baseline Subtraction Error' 메시지가 발생합니다."
  {...register("question")}
/>
```

---

## 모바일 특화

| 요소 | 데스크톱 | 모바일 |
|------|---------|--------|
| 제품군 칩 | 한 줄 wrap | 스크롤 가능 wrap |
| 채널/톤 | 2열 횡배치 | 1열 종배치 |
| Drop Zone | 넓은 영역 | 컴팩트 (p-6) |
| CTA | static | sticky bottom (gradient fade) |
| 파일 프리뷰 | 좌우 배치 | 좌우 배치 (유지) |

---

## 접근성 체크리스트

- [x] `aria-invalid` on error state
- [x] `aria-describedby` linking error messages
- [x] `aria-label` on file input and remove button
- [ ] 추가: `aria-live="polite"` on toast area
- [ ] 추가: 제품군 토글에 `role="group" aria-label="제품군 선택"`
- [ ] 추가: 제품군 칩에 `aria-pressed={selected}`
