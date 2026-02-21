# PRD: PDF 미리보기 확대 모달 + 선택적 다운로드 + 인라인 Citation

## 1. 개요

| 항목 | 내용 |
|------|------|
| 프로젝트 | Bio-Rad CS Copilot |
| 작성자 | Product Manager |
| 작성일 | 2026-02-20 |
| 우선순위 | High |
| 예상 Sprint | 1 Sprint (2주) |

### 1.1 배경
CS 담당자가 답변 초안의 근거 문서를 검토할 때, 현재 스플릿 패널의 미리보기 영역이 좁아 문서 내용을 정확히 확인하기 어려움. 또한 다운로드 시 전체 문서만 받을 수 있어, 근거로 제시된 특정 페이지만 빠르게 공유하거나 저장하는 것이 불가능함. 추가로, 답변 본문에 어떤 근거에서 어떤 내용이 나왔는지 인라인 출처 표기가 없어 신뢰성 검증이 어려움.

### 1.2 목표
1. PDF 확대 모달을 통해 문서를 크고 편하게 열람
2. 전체 문서 다운로드와 근거 페이지만 다운로드 기능 분리
3. 답변 본문에 인라인 citation 삽입으로 근거 추적성 확보

## 2. 사용자 스토리

### US-1: PDF 확대 모달
> CS 담당자로서, 미리보기에서 "확대" 버튼을 클릭하면 화면 중앙에 큰 모달이 열려 확대된 상태로 문서를 볼 수 있다. 모달 내에서 페이지 이동과 다운로드가 가능하다.

**인수 조건:**
- 미리보기 영역에 "확대" 버튼 추가
- 클릭 시 화면 중앙에 오버레이 모달 (viewport 90% 크기)
- 모달 내에서 이전/다음 페이지 네비게이션
- 모달 내에서 다운로드 (전체/근거 페이지) 가능
- ESC 키 또는 바깥 클릭으로 닫기
- 키보드 접근성 (focus trap, aria-modal)

### US-2: 선택적 다운로드
> CS 담당자로서, 근거로 제시된 특정 페이지만 다운로드하거나 전체 문서를 다운로드할 수 있다.

**인수 조건:**
- 다운로드 버튼을 드롭다운으로 변경
  - "전체 문서 다운로드" → 기존 `/documents/{id}/download` API
  - "근거 페이지만 다운로드" → 기존 `/documents/{id}/pages?from=X&to=Y` API (attachment disposition)
- 근거 페이지 다운로드 시 파일명: `{원본명}_p{from}-{to}.pdf`
- 모달 내부와 미리보기 패널 양쪽에서 동일하게 작동

### US-3: 인라인 Citation
> CS 담당자로서, 답변 본문에서 각 주장이 어떤 문서의 몇 페이지에서 나왔는지 확인할 수 있다.

**인수 조건:**
- 답변 본문 내에 자연스러운 한국어 출처 표기 삽입 (예: "~로 확인됩니다 (10000107223.pdf, p.104-105)")
- 출처 표기 클릭 시 해당 근거의 미리보기로 이동 (selectedEvidence 변경)
- OpenAI ComposeStep 프롬프트에 인라인 citation 규칙 추가
- DefaultComposeStep에도 기본 citation 패턴 적용

## 3. 기술 설계

### 3.1 프론트엔드 변경

#### 3.1.1 PdfExpandModal (신규 컴포넌트)
- `frontend/src/components/ui/PdfExpandModal.tsx`
- Props: `url`, `numPages`, `initialPage`, `downloadUrl`, `pagesDownloadUrl`, `fileName`, `onClose`
- shadcn/ui Dialog 기반, 90vw x 90vh 크기
- react-pdf 동일 사용, pageWidth를 모달 너비에 맞춤
- 페이지 네비게이션 + 다운로드 드롭다운

#### 3.1.2 PdfViewer 수정
- "확대" 버튼 추가 → PdfExpandModal 열기
- 다운로드 버튼 → DropdownMenu로 변경 (전체 다운로드 / 근거 페이지 다운로드)
- 새 Props: `pagesDownloadUrl?`, `onExpand?`

#### 3.1.3 InquiryAnswerTab 수정
- `pagesDownloadUrl`을 PdfViewer에 전달
- 답변 본문 렌더링 시 citation 패턴 `(파일명, p.XX-YY)` 감지하여 클릭 가능한 링크로 변환
- 링크 클릭 시 해당 evidence 선택 → 미리보기 패널 업데이트

### 3.2 백엔드 변경

#### 3.2.1 DocumentDownloadController 수정
- `/documents/{id}/pages` 엔드포인트의 `Content-Disposition`을 `attachment`로 변경하는 `?download=true` 옵션 추가

#### 3.2.2 OpenAiComposeStep 프롬프트 수정
- 시스템 프롬프트 규칙 6번 수정: 번호 인용 금지 → 파일명+페이지 기반 자연어 인용으로 변경
- 요구사항에 인라인 citation 규칙 추가:
  - 각 주장의 근거가 되는 참고 자료의 파일명과 페이지 번호를 본문 내에 자연스럽게 포함
  - 형식: `(파일명, p.XX)` 또는 `(파일명, p.XX-YY)`
  - 참고 자료 목록에 파일명과 페이지 정보 포함

#### 3.2.3 DefaultComposeStep 수정
- 템플릿 기반 답변에도 citation placeholder 삽입

## 4. 비기능 요구사항

| 항목 | 기준 |
|------|------|
| 접근성 | 모달에 aria-modal, focus trap, ESC 닫기 |
| 반응형 | 모바일에서는 모달 대신 전체화면 |
| 성능 | 모달 열 때 PDF 재로딩 없음 (캐시 활용) |
| 브라우저 | Chrome, Edge, Safari 최신 2개 버전 |

## 5. 작업 분해 (WBS)

| # | 작업 | 담당 | 의존성 |
|---|------|------|--------|
| T1 | PdfExpandModal 컴포넌트 구현 | Frontend | - |
| T2 | PdfViewer에 확대 버튼 + 다운로드 드롭다운 추가 | Frontend | T1 |
| T3 | InquiryAnswerTab에 citation 클릭 핸들링 추가 | Frontend | T2 |
| T4 | OpenAiComposeStep 프롬프트에 인라인 citation 규칙 추가 | Backend | - |
| T5 | DefaultComposeStep에 citation 패턴 적용 | Backend | - |
| T6 | DocumentDownloadController에 download=true 옵션 추가 | Backend | - |
| T7 | 통합 테스트 + UI 검증 | QA | T1-T6 |

## 6. 범위 제외 (Out of Scope)
- PDF 텍스트 하이라이트 (근거 문장 강조)
- Word/Excel 문서 미리보기
- 모바일 앱 대응
