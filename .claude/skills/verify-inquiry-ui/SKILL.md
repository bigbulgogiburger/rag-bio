---
name: verify-inquiry-ui
description: 문의 상세 페이지 UI 컴포넌트 검증 (shadcn/ui + Tailwind 기준). 문의 탭(Info/Analysis/Answer/History) 및 업로드 컴포넌트 수정 후 사용.
---

## Purpose

1. **Citation 파싱 일관성** — AnswerTab과 HistoryTab의 parseCitation이 백엔드 citations 형식과 동기화되는지 확인
2. **Split Pane 레이아웃** — Tailwind grid + lg: 반응형으로 split pane 구현 확인
3. **PdfViewer SSR 제한** — AnswerTab과 HistoryTab에서 dynamic import + ssr: false 사용 확인
4. **sourceType 뱃지 매핑** — KNOWLEDGE_BASE="지식 기반"/INQUIRY="문의 첨부" 일관성 확인
5. **한국어 라벨 함수 사용** — 영문 enum 값을 직접 표시하지 않고 labelVerdict/labelDocStatus 등 사용 확인
6. **shadcn 컴포넌트 사용** — Button, Badge, Skeleton 등 shadcn/커스텀 UI 컴포넌트 사용 확인
7. **react-hook-form + zod** — InquiryCreateForm에서 폼 유효성 검사 확인

## When to Run

- `frontend/src/components/inquiry/` 하위 컴포넌트 수정 후
- `frontend/src/components/upload/` 하위 컴포넌트 수정 후
- `frontend/src/lib/api/client.ts`의 타입/함수 변경 후
- 답변 초안 표시 로직 변경 후
- 근거 표시 형식 변경 후

## Related Files

| File | Purpose |
|------|---------|
| `frontend/src/components/inquiry/InquiryAnswerTab.tsx` | 답변 탭 (Tailwind grid split pane + Citation 파싱 + PdfViewer + translatedQuery 표시) |
| `frontend/src/components/inquiry/InquiryCreateForm.tsx` | 문의 생성 폼 (react-hook-form + zod + shadcn Button) |
| `frontend/src/components/inquiry/InquiryInfoTab.tsx` | 정보 탭 (문서 목록 + 인덱싱 상태 + Skeleton) |
| `frontend/src/components/inquiry/InquiryHistoryTab.tsx` | 이력 탭 (버전 히스토리 + Tailwind grid split pane + PdfViewer) |
| `frontend/src/components/upload/SmartUploadModal.tsx` | 스마트 업로드 모달 (KB 문서 일괄 업로드) |
| `frontend/src/components/upload/FileDropZone.tsx` | 파일 드래그앤드롭 영역 (cn + Tailwind) |
| `frontend/src/components/upload/FileQueueItem.tsx` | 파일 큐 아이템 (cn + status 기반 스타일) |
| `frontend/src/lib/api/client.ts` | API 클라이언트 (타입 정의 + 헬퍼 함수) |
| `frontend/src/lib/i18n/labels.ts` | 한국어 라벨 매핑 함수 (labelReviewDecision, labelApprovalDecision 포함) |

## Workflow

### Step 1: Citation 파싱 정규식 확인

**파일:** `InquiryAnswerTab.tsx`, `InquiryHistoryTab.tsx`

**검사:** parseCitation 함수가 6개 키 (chunk, score, documentId, fileName, pageStart, pageEnd)를 파싱하는지 확인.

```bash
grep -n "chunk=\|score=\|documentId=\|fileName=\|pageStart=\|pageEnd=" frontend/src/components/inquiry/InquiryAnswerTab.tsx frontend/src/components/inquiry/InquiryHistoryTab.tsx
```

**PASS:** 양쪽 모두 6개 키 정규식 매칭 존재
**FAIL:** 키 누락 또는 fileName regex 오류

### Step 2: Tailwind Grid Split Pane 확인

**파일:** `InquiryAnswerTab.tsx`, `InquiryHistoryTab.tsx`

**검사:** split pane이 Tailwind grid (`grid grid-cols-1 gap-6 lg:grid-cols-[1fr_400px]`)로 구현되었는지 확인.

```bash
grep -n "grid.*cols\|lg:grid-cols\|lg:sticky\|lg:self-start" frontend/src/components/inquiry/InquiryAnswerTab.tsx frontend/src/components/inquiry/InquiryHistoryTab.tsx
```

**PASS:** Tailwind grid + lg: 반응형 + sticky 오른쪽 패널 존재
**FAIL:** 레거시 `.split-pane` CSS 클래스 사용 또는 split pane 누락

### Step 3: PdfViewer dynamic import + ssr: false 확인

**파일:** `InquiryAnswerTab.tsx`, `InquiryHistoryTab.tsx`

**검사:** PdfViewer를 next/dynamic으로 ssr: false 설정하여 임포트하는지 확인.

```bash
grep -rn "dynamic\|ssr.*false\|PdfViewer" frontend/src/components/inquiry/InquiryAnswerTab.tsx frontend/src/components/inquiry/InquiryHistoryTab.tsx
```

**PASS:** 양쪽 모두 `dynamic(() => import(...), { ssr: false })` 패턴 존재
**FAIL:** 직접 import 사용

### Step 4: 답변 본문 whitespace-pre-wrap 확인

**파일:** `InquiryAnswerTab.tsx`, `InquiryHistoryTab.tsx`

**검사:** 답변 초안 본문에 Tailwind `whitespace-pre-wrap` 클래스가 적용되는지 확인.

```bash
grep -rn "whitespace-pre-wrap\|pre-wrap" frontend/src/components/inquiry/InquiryAnswerTab.tsx frontend/src/components/inquiry/InquiryHistoryTab.tsx
```

**PASS:** 답변 본문 표시 영역에 `whitespace-pre-wrap` 클래스 적용
**FAIL:** `whitespace-pre-wrap` 없음

### Step 5: sourceType 뱃지 매핑 일관성

**파일:** `InquiryAnswerTab.tsx`

**검사:** KNOWLEDGE_BASE="지식 기반" (info), INQUIRY/else="문의 첨부" (neutral) 매핑이 AnswerTab에서 올바르게 적용되는지 확인. (InquiryAnalysisTab.tsx는 삭제됨 — 분석 결과가 AnswerTab에 통합)

```bash
grep -rn "지식 기반\|문의 첨부\|KNOWLEDGE_BASE\|sourceType" frontend/src/components/inquiry/InquiryAnswerTab.tsx
```

**PASS:** KNOWLEDGE_BASE="지식 기반" (info), INQUIRY="문의 첨부" (neutral) 매핑 존재
**FAIL:** 뱃지 variant 또는 라벨 불일치

### Step 6: 한국어 라벨 함수 사용 확인

**파일:** `frontend/src/components/inquiry/*.tsx`

**검사:** 영문 enum 값을 직접 표시하지 않고 labels.ts의 함수를 통해 변환하는지 확인.

```bash
grep -rn "labelVerdict\|labelAnswerStatus\|labelDocStatus\|labelChannel\|labelTone" frontend/src/components/inquiry/
```

**PASS:** 4개 이상의 라벨 함수 사용
**FAIL:** 영문 enum 값 직접 표시

### Step 7: shadcn Button 사용 확인

**파일:** `frontend/src/components/inquiry/*.tsx`

**검사:** 모든 inquiry 컴포넌트에서 레거시 `.btn` 클래스 대신 shadcn Button 컴포넌트를 사용하는지 확인.

```bash
grep -rn "from.*button\|className=\"btn" frontend/src/components/inquiry/*.tsx
```

**PASS:** shadcn Button import 존재, `.btn` 클래스 없음
**FAIL:** 레거시 `.btn` 클래스 사용

### Step 8: react-hook-form + zod 스키마 확인

**파일:** `InquiryCreateForm.tsx`

**검사:** react-hook-form의 useForm + zodResolver를 사용하고, zod 스키마가 정의되어 있는지 확인.

```bash
grep -rn "useForm\|zodResolver\|z\.object\|z\.string\|z\.enum" frontend/src/components/inquiry/InquiryCreateForm.tsx
```

**PASS:** useForm + zodResolver + z.object 스키마 존재
**FAIL:** 수동 상태 관리 또는 유효성 검사 누락

### Step 9: 파일 업로드 accept 속성 확인

**파일:** `InquiryCreateForm.tsx`, `FileDropZone.tsx`

**검사:** 파일 업로드에 허용 파일 형식이 지정되어 있는지 확인.

```bash
grep -n "accept\|\.pdf\|\.doc" frontend/src/components/inquiry/InquiryCreateForm.tsx frontend/src/components/upload/FileDropZone.tsx
```

**PASS:** accept 속성에 PDF/DOC/DOCX 지정
**FAIL:** accept 속성 없음

### Step 10: API 클라이언트 다운로드 URL 헬퍼 확인

**파일:** `frontend/src/lib/api/client.ts`

**검사:** `getDocumentDownloadUrl`과 `getDocumentPagesUrl` 헬퍼 함수가 존재하는지 확인.

```bash
grep -n "getDocumentDownloadUrl\|getDocumentPagesUrl" frontend/src/lib/api/client.ts
```

**PASS:** 두 함수 모두 존재
**FAIL:** 함수 누락

### Step 11: AI 워크플로우 UI 확인

**파일:** `InquiryAnswerTab.tsx`

**검사:** AI 자동 워크플로우(autoWorkflow), 리뷰 상세(reviewDetail), 승인 게이트 결과(gateResults) UI가 존재하고 올바르게 표시되는지 확인.

```bash
grep -n "autoWorkflow\|reviewDetail\|gateResults\|labelReviewDecision\|labelApprovalDecision" frontend/src/components/inquiry/InquiryAnswerTab.tsx
```

**PASS:** autoWorkflow 체크박스, reviewDetail 패널, gateResults 4개 게이트 표시 존재
**FAIL:** AI 워크플로우 UI 없음 또는 라벨 함수 미사용

### Step 12: 답변 본문 수정(editing) UI 확인

**파일:** `InquiryAnswerTab.tsx`

**검사:** 답변 본문 수정 기능이 textarea + 저장/취소 버튼으로 구현되고, SENT 상태에서 수정 버튼이 숨겨지는지 확인.

```bash
grep -n "isEditing\|editedDraft\|handleSaveDraft\|textarea\|수정\|저장\|취소" frontend/src/components/inquiry/InquiryAnswerTab.tsx
```

**PASS:** isEditing 토글, textarea, 저장/취소 버튼, SENT 상태 시 수정 버튼 숨김
**FAIL:** 수정 기능 없음 또는 SENT 상태에서 수정 가능

### Step 13: sendRequestId 고유성 확인

**파일:** `InquiryAnswerTab.tsx`

**검사:** 전송 요청 ID가 `Date.now()` 등을 활용하여 매번 고유한 값을 생성하는지 확인. 정적 문자열 사용 시 재전송 방지가 동작하지 않음.

```bash
grep -n "sendRequestId\|Date.now\|requestId" frontend/src/components/inquiry/InquiryAnswerTab.tsx
```

**PASS:** `Date.now()` 또는 UUID 등 동적 값으로 requestId 생성
**FAIL:** 정적 문자열 (예: `-send-v1`) 사용

### Step 14: preferredTone 초기값 자동 적용 확인

**파일:** `InquiryAnswerTab.tsx`, `frontend/src/lib/api/client.ts`

**검사:** InquiryDetail에 `preferredTone` 필드가 있고, AnswerTab에서 초기 tone 값으로 사용되는지 확인.

```bash
grep -n "preferredTone\|initialTone\|selectedTone" frontend/src/components/inquiry/InquiryAnswerTab.tsx frontend/src/lib/api/client.ts
```

**PASS:** client.ts에 preferredTone 타입 정의 + AnswerTab에서 초기값으로 사용
**FAIL:** preferredTone 필드 미정의 또는 초기값 미적용

### Step 15: translatedQuery 영어 번역 표시 확인

**파일:** `InquiryAnswerTab.tsx`

**검사:** 하이브리드 검색 시 한국어 질문이 영어로 번역된 경우, 번역 결과(`translatedQuery`)가 UI에 표시되는지 확인. 원본 질문과 동일하면 표시하지 않아야 함.

```bash
grep -n "translatedQuery" frontend/src/components/inquiry/InquiryAnswerTab.tsx
```

**PASS:** `translatedQuery` 필드를 조건부로 표시 (원본과 다를 때만)하고 italic 스타일 적용
**FAIL:** `translatedQuery` 표시 로직 없음 또는 무조건 표시

## Output Format

| # | 검사 항목 | 결과 | 상세 |
|---|----------|------|------|
| 1 | Citation 파싱 정규식 | PASS/FAIL | 누락 키, regex 방식 |
| 2 | Tailwind Grid Split Pane | PASS/FAIL | grid 패턴, lg: 반응형 |
| 3 | PdfViewer SSR 제한 | PASS/FAIL | import 방식 |
| 4 | whitespace-pre-wrap | PASS/FAIL | 미적용 컴포넌트 |
| 5 | sourceType 뱃지 매핑 | PASS/FAIL | 불일치 위치 |
| 6 | 한국어 라벨 함수 | PASS/FAIL | 직접 표시 위치 |
| 7 | shadcn Button 사용 | PASS/FAIL | 레거시 .btn 사용처 |
| 8 | react-hook-form + zod | PASS/FAIL | 누락 패턴 |
| 9 | 파일 업로드 accept | PASS/FAIL | accept 속성 |
| 10 | 다운로드 URL 헬퍼 | PASS/FAIL | 함수 유무 |
| 11 | AI 워크플로우 UI | PASS/FAIL | autoWorkflow/reviewDetail/gateResults |
| 12 | 답변 본문 수정 UI | PASS/FAIL | textarea + 저장/취소 + SENT 숨김 |
| 13 | sendRequestId 고유성 | PASS/FAIL | Date.now() 사용 |
| 14 | preferredTone 초기값 | PASS/FAIL | 타입 정의 + 자동 적용 |
| 15 | translatedQuery 표시 | PASS/FAIL | 조건부 표시 + italic 스타일 |

## Exceptions

다음은 **위반이 아닙니다**:

1. **3단계 폴백 표시** — fileName → documentId.slice(0,8) → chunkId.slice(0,8) 순서는 레거시 호환
2. **SmartUploadModal의 category 기본값** — 기본값이 빈 문자열인 것은 의도된 동작 (사용자 선택 유도)
3. **InquiryCreateForm의 file useState** — react-hook-form 외부에서 파일 상태를 관리하는 것은 File 객체의 직렬화 제한 때문
