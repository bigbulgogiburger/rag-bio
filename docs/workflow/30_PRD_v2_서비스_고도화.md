# PRD v2 — Bio-Rad CS Copilot 서비스 고도화

> **문서 버전:** 2.0
> **작성일:** 2026-02-13
> **작성자:** IT Agile Planning
> **상태:** Draft → Review 대기

---

## 0. 문서 목적

Sprint 1-6을 통해 MVP 파이프라인(질문 접수 → 문서 업로드 → 인덱싱 → RAG 검색 → 판정 → 답변 초안 → 승인 → 발송)이 구축되었다.

본 PRD는 **실제 운영 투입 전에 반드시 해소해야 할 4가지 핵심 문제**를 정의하고, 이를 해결하기 위한 기능 요구사항과 실행 계획을 기술한다.

---

## 1. 현황 분석 — "지금 뭐가 문제인가?"

### 1.1 문제 정의

| # | 문제 | 심각도 | 현재 상태 |
|---|------|--------|----------|
| P1 | **지식 기반(Knowledge Base) 관리 부재** | Critical | 문서 업로드가 개별 문의(Inquiry)에 종속됨. Bio-Rad가 보유한 기술 매뉴얼·프로토콜·제품 스펙 등 **참조 자료를 사전에 올려두고 전체 문의에서 공통으로 검색할 수 있는 장소가 없음** |
| P2 | **영문 결과 표시** | High | 판정(SUPPORTED/REFUTED/CONDITIONAL), 리스크 플래그, 상태(DRAFT/REVIEWED 등), 에러 메시지가 모두 영어로 표시되어 현업 CS 담당자가 즉시 이해하기 어려움 |
| P3 | **문의 이력 페이징 없음** | High | 문의 목록 조회 API 자체가 존재하지 않고, 문의 ID를 직접 입력해야 조회 가능. 과거 대응 내역 추적·검색 불가 |
| P4 | **프로토타입 수준 UI/UX** | Medium | 단일 페이지에 모든 기능이 수직 나열됨. 워크플로우 단계별 분리 없음, 반응형 미흡, 디자인 시스템 부재 |

### 1.2 근본 원인 분석

```
P1 → 현 아키텍처: Document ──(FK)──> Inquiry (1:N)
     문의 없이는 문서를 올릴 수 없는 구조.
     Vector Store 검색도 inquiry 단위 chunk만 대상.

P2 → 백엔드 Enum/상수가 영문 그대로 API 응답에 노출.
     프론트엔드 레이블 매핑 없음.

P3 → GET /api/v1/inquiries (목록) 엔드포인트 자체 미구현.
     InquiryJpaRepository에 페이징 쿼리 없음.

P4 → inquiry-form.tsx 단일 파일(600+ LOC)에 전체 플로우 집약.
     디자인 토큰·공통 컴포넌트 체계 없음.
```

---

## 2. 목표 (Goals)

### 2.1 비즈니스 목표

| 목표 | 측정 지표 | 목표값 |
|------|----------|--------|
| CS 담당자가 참조 문서를 **자율적으로** 관리 | 지식 기반 문서 등록 건수 | 운영 첫 주 10건 이상 |
| 화면 내 **모든 텍스트**를 한국어로 즉시 이해 | 주요 화면 한국어 비율 | 100% |
| 과거 문의-대응 내역을 **5초 이내** 검색 | 문의 목록 로드 시간 | P95 ≤ 2초 |
| CS 담당자 **온보딩 시간 단축** | 신규 사용자 첫 작업 완료 시간 | 10분 이내 |

### 2.2 비목표 (Non-Goals)

- 외부 고객 직접 접근 포털 구축
- 모바일 네이티브 앱
- 다국어(영어/일어 등) 동시 지원 — 한국어 단일화 우선
- 기존 백엔드 도메인 로직(판정 알고리즘, 오케스트레이션 등) 변경

---

## 3. 기능 요구사항

---

### 3.1 [P1] 지식 기반(Knowledge Base) 관리

#### 3.1.1 문제 상세

현재 시스템은 다음 흐름만 지원한다:

```
고객 질문 접수 → 질문에 첨부된 문서 업로드 → 해당 문서만 인덱싱 → 해당 chunk만 검색
```

**빠져 있는 것:**

Bio-Rad가 보유한 **기술 문서·매뉴얼·프로토콜·제품 스펙·FAQ** 등을 **문의와 무관하게** 미리 업로드하고 벡터화해두면, 모든 문의에서 해당 지식을 **공통 참조**할 수 있어야 한다.

예시:
- "Reagent X 4°C 야간 보관" 질문이 들어왔을 때,
  → 문의에 첨부된 고객 문서 **+** 사전 등록된 Reagent X 매뉴얼에서 동시에 근거를 검색

#### 3.1.2 해결 방안: Knowledge Base 모듈

**새로운 개념 모델:**

```
┌─────────────────────────────────────────────┐
│               Vector Store                   │
│  ┌─────────────┐   ┌────────────────────┐   │
│  │ Inquiry Docs│   │ Knowledge Base Docs│   │
│  │ (문의 첨부) │   │ (사전 등록 참조)   │   │
│  └─────────────┘   └────────────────────┘   │
│         ↑ 검색 대상 = 양쪽 모두 통합         │
└─────────────────────────────────────────────┘
```

**엔티티 설계:**

```
knowledge_documents (새 테이블)
├── id: UUID (PK)
├── title: VARCHAR(500)          -- 문서 제목
├── category: VARCHAR(100)       -- 분류 (매뉴얼/프로토콜/FAQ/스펙시트)
├── product_family: VARCHAR(200) -- 제품군 (Reagent/Instrument/Software)
├── file_name: VARCHAR(255)
├── content_type: VARCHAR(100)
├── file_size: BIGINT
├── storage_path: VARCHAR(500)
├── status: VARCHAR(40)          -- UPLOADED/INDEXED/FAILED
├── description: VARCHAR(2000)   -- 문서 설명 (선택)
├── tags: VARCHAR(500)           -- 검색용 태그 (쉼표 구분)
├── uploaded_by: VARCHAR(120)    -- 등록자
├── chunk_count: INT
├── vector_count: INT
├── created_at: TIMESTAMPTZ
├── updated_at: TIMESTAMPTZ
└── INDEX idx_kb_category, idx_kb_product_family, idx_kb_status
```

**API 설계:**

| Method | Endpoint | 설명 |
|--------|----------|------|
| `POST` | `/api/v1/knowledge-base/documents` | 지식 기반 문서 업로드 (multipart + 메타데이터) |
| `GET` | `/api/v1/knowledge-base/documents` | 문서 목록 조회 (페이징, 카테고리/제품군 필터) |
| `GET` | `/api/v1/knowledge-base/documents/{docId}` | 문서 상세 |
| `DELETE` | `/api/v1/knowledge-base/documents/{docId}` | 문서 삭제 (벡터 포함) |
| `POST` | `/api/v1/knowledge-base/documents/{docId}/indexing/run` | 개별 문서 인덱싱 실행 |
| `POST` | `/api/v1/knowledge-base/indexing/run` | 전체 미인덱싱 문서 일괄 인덱싱 |
| `GET` | `/api/v1/knowledge-base/stats` | 지식 기반 통계 (총 문서수, 카테고리별 분포 등) |

**검색 통합:**

기존 `AnalysisService.retrieve()` 수정:
```
AS-IS: inquiry의 chunk만 검색
TO-BE: inquiry chunk + knowledge_base chunk 통합 검색
       → VectorStore.search() 결과에 source 구분 필드 추가
       → EvidenceItem에 sourceType: "INQUIRY_DOC" | "KNOWLEDGE_BASE" 추가
```

**UI 화면:**

```
/knowledge-base (새 페이지)
├── 상단: "지식 기반 관리" 제목 + "문서 등록" 버튼
├── 필터 바: [카테고리 ▼] [제품군 ▼] [상태 ▼] [검색어 입력]
├── 문서 목록 테이블:
│   ├── 제목 | 카테고리 | 제품군 | 상태 | 청크 수 | 등록일 | 등록자
│   └── 페이징 (10건/20건/50건)
├── 문서 등록 모달/페이지:
│   ├── 파일 드래그 앤 드롭 영역
│   ├── 제목 (필수)
│   ├── 카테고리 선택 (매뉴얼/프로토콜/FAQ/스펙시트)
│   ├── 제품군 선택
│   ├── 설명 (선택)
│   ├── 태그 (선택)
│   └── "등록" 버튼
└── 문서 상세:
    ├── 메타데이터 카드
    ├── 인덱싱 상태 + "인덱싱 실행" 버튼
    └── "삭제" 버튼 (확인 다이얼로그)
```

**수용 기준 (Acceptance Criteria):**

- [ ] CS 담당자가 문의 없이 기술 문서를 업로드할 수 있다
- [ ] 업로드된 문서가 인덱싱되어 벡터 스토어에 저장된다
- [ ] 문의 분석 시, 문의 첨부 문서 + 지식 기반 문서 양쪽에서 근거를 검색한다
- [ ] 검색 결과에 출처 구분(문의 첨부 / 지식 기반)이 표시된다
- [ ] 카테고리·제품군별 필터링과 페이징이 동작한다

---

### 3.2 [P2] 전면 한국어화

#### 3.2.1 문제 상세

현재 영문으로 표시되는 항목들:

| 영역 | 현재 (영문) | 개선 (한국어) |
|------|-----------|-------------|
| 판정 | SUPPORTED / REFUTED / CONDITIONAL | 근거 충분 / 근거 부족 / 조건부 |
| 상태 | DRAFT / REVIEWED / APPROVED / SENT | 초안 / 검토 완료 / 승인 완료 / 발송 완료 |
| 문서 상태 | UPLOADED / PARSING / PARSED / CHUNKED / INDEXED / FAILED_PARSING | 업로드됨 / 파싱 중 / 파싱 완료 / 청크 완료 / 인덱싱 완료 / 파싱 실패 |
| 톤 | professional / technical / brief | 정중체 / 기술 상세 / 요약 |
| 채널 | email / messenger | 이메일 / 메신저 |
| 리스크 플래그 | LOW_CONFIDENCE, WEAK_EVIDENCE_MATCH, CONFLICTING_EVIDENCE, FALLBACK_DRAFT_USED | 신뢰도 낮음, 근거 약함, 근거 상충, 대체 초안 사용 |
| 에러 메시지 | 영문 기술 메시지 | 한국어 안내 메시지 |

#### 3.2.2 해결 방안

**프론트엔드 라벨 매핑 시스템:**

```typescript
// src/lib/i18n/labels.ts (신규)

export const VERDICT_LABELS: Record<string, string> = {
  SUPPORTED:   "근거 충분",
  REFUTED:     "근거 부족",
  CONDITIONAL: "조건부",
};

export const STATUS_LABELS: Record<string, string> = {
  DRAFT:    "초안",
  REVIEWED: "검토 완료",
  APPROVED: "승인 완료",
  SENT:     "발송 완료",
};

export const DOC_STATUS_LABELS: Record<string, string> = {
  UPLOADED:       "업로드됨",
  PARSING:        "파싱 중",
  PARSED:         "파싱 완료",
  PARSED_OCR:     "OCR 파싱 완료",
  CHUNKED:        "청크 완료",
  INDEXED:        "인덱싱 완료",
  FAILED_PARSING: "파싱 실패",
};

export const RISK_FLAG_LABELS: Record<string, string> = {
  LOW_CONFIDENCE:         "신뢰도 낮음",
  WEAK_EVIDENCE_MATCH:    "근거 약함",
  CONFLICTING_EVIDENCE:   "근거 상충",
  FALLBACK_DRAFT_USED:    "대체 초안 사용",
  ORCHESTRATION_FALLBACK: "처리 중 오류 발생",
};

export const ERROR_LABELS: Record<string, string> = {
  AUTH_USER_ID_REQUIRED: "사용자 ID가 필요합니다",
  AUTH_ROLE_FORBIDDEN:   "권한이 부족합니다",
  INVALID_STATE:         "현재 상태에서 수행할 수 없는 작업입니다",
  NOT_FOUND:             "요청한 항목을 찾을 수 없습니다",
};
```

**적용 원칙:**
1. API 응답값은 영문 Enum 유지 (하위 호환, 기술적 정합성)
2. **프론트엔드 표시 시점**에 한국어 라벨로 변환
3. 매핑 테이블에 없는 값은 원문 그대로 표시 (안전 장치)
4. 답변 초안 본문은 이미 한국어 생성 — 변경 불필요

**수용 기준:**

- [ ] 판정·상태·리스크 플래그·채널·톤이 모두 한국어로 표시된다
- [ ] 에러 메시지가 한국어로 표시된다
- [ ] API 응답 스키마는 변경되지 않는다 (영문 Enum 유지)
- [ ] 매핑되지 않은 새 값이 추가되어도 화면이 깨지지 않는다

---

### 3.3 [P3] 문의 목록 조회 + 페이징

#### 3.3.1 문제 상세

현재 시스템에는 **문의 목록을 조회하는 기능이 전혀 없다.**

- `GET /api/v1/inquiries` 엔드포인트 미구현
- 문의 ID를 직접 입력해야만 조회 가능
- 과거 문의 검색·정렬·필터링 불가

#### 3.3.2 해결 방안

**API 설계:**

```
GET /api/v1/inquiries
  ?page=0
  &size=20
  &sort=createdAt,desc
  &status=RECEIVED,ANALYZED
  &channel=email
  &keyword=reagent
  &from=2026-01-01T00:00:00Z
  &to=2026-02-13T23:59:59Z
```

**응답 스키마:**

```json
{
  "content": [
    {
      "inquiryId": "uuid",
      "question": "질문 내용 (요약, 최대 200자)",
      "customerChannel": "email",
      "status": "RECEIVED",
      "documentCount": 3,
      "latestAnswerStatus": "APPROVED",
      "createdAt": "2026-02-13T12:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 142,
  "totalPages": 8
}
```

**UI 화면:**

```
/inquiries (새 페이지 — 네비게이션에 "문의 목록" 추가)
├── 상단: "문의 대응 내역" 제목
├── 필터 바:
│   ├── [상태 ▼] [채널 ▼] [기간: 시작일 ~ 종료일]
│   ├── [검색어 입력 (질문 내용 키워드)]
│   └── [검색] 버튼
├── 문의 목록 테이블:
│   ├── 컬럼: 접수일 | 질문 요약 | 채널 | 문의 상태 | 답변 상태 | 문서 수
│   ├── 각 행 클릭 → /inquiries/{id} 상세 페이지로 이동
│   └── 상태 배지 (한국어, 컬러 코딩)
├── 페이징:
│   ├── [◀ 이전] [1] [2] [3] ... [▶ 다음]
│   └── "전체 142건 중 1-20건"
└── 건수 선택: [20건 ▼] / 50건 / 100건
```

**수용 기준:**

- [ ] 문의 목록이 페이징되어 조회된다 (기본 20건)
- [ ] 상태·채널·기간·키워드로 필터링할 수 있다
- [ ] 최신순/오래된순 정렬이 가능하다
- [ ] 목록에서 클릭 시 해당 문의 상세 페이지로 이동한다
- [ ] 142건의 문의가 있을 때 P95 응답 시간 ≤ 2초

---

### 3.4 [P4] UI/UX 현대화

#### 3.4.1 문제 상세

현재 UI 구조:
```
/dashboard          — 운영 대시보드 (메트릭 6개 카드)
/inquiry/new        — 문의 작성 + 조회 + 인덱싱 + 분석 + 초안 + 승인 + 발송
                      (inquiry-form.tsx 단일 파일, 600+ LOC, 모든 기능 수직 나열)
```

**문제점:**
- 한 화면에 7개 이상의 워크플로우 단계가 밀집
- 처음 사용하는 CS 담당자가 어디서부터 시작할지 알 수 없음
- 반응형 지원 미흡
- 디자인 시스템 없음 (인라인 스타일 + 글로벌 CSS 혼재)

#### 3.4.2 해결 방안: 페이지 분리 + 디자인 시스템

**새로운 IA (Information Architecture):**

```
/                        → /dashboard로 리다이렉트
/dashboard               → 운영 대시보드 (메트릭 + 최근 문의 요약)
/inquiries               → [신규] 문의 목록 (검색 + 필터 + 페이징)
/inquiries/new           → [이동] 문의 작성 (질문 + 문서 업로드)
/inquiries/{id}          → [신규] 문의 상세 (탭 기반)
  ├── 탭: 기본 정보      → 문의 내용 + 문서 목록 + 인덱싱 상태
  ├── 탭: 분석           → 근거 검색 + 판정 결과
  ├── 탭: 답변           → 초안 생성 + 리뷰 + 승인 + 발송
  └── 탭: 이력           → 버전 히스토리 + 감사 로그
/knowledge-base          → [신규] 지식 기반 문서 관리
```

**디자인 시스템 기반 컴포넌트:**

```
src/components/ui/ (공통 컴포넌트)
├── Button.tsx        — primary / secondary / danger / ghost 변형
├── Badge.tsx         — 상태 배지 (색상 + 한국어 라벨 자동 매핑)
├── Card.tsx          — 기본 카드 레이아웃
├── DataTable.tsx     — 정렬 + 페이징 테이블
├── Modal.tsx         — 확인/입력 모달
├── Toast.tsx         — 성공/실패/경고 토스트
├── Tabs.tsx          — 탭 네비게이션
├── FileUpload.tsx    — 드래그 앤 드롭 파일 업로드
├── FilterBar.tsx     — 검색 + 필터 조합 바
├── Timeline.tsx      — 워크플로우 진행 타임라인
├── EmptyState.tsx    — 데이터 없음 안내
└── Spinner.tsx       — 로딩 표시
```

**디자인 토큰:**

```css
/* 기존 globals.css의 CSS 변수 체계를 확장 */
:root {
  /* 색상 — 기존 유지 + 확장 */
  --primary: #2563eb;
  --primary-hover: #1d4ed8;
  --success: #16a34a;
  --warn: #d97706;
  --danger: #dc2626;
  --info: #0284c7;

  /* 간격 */
  --space-xs: 4px;
  --space-sm: 8px;
  --space-md: 16px;
  --space-lg: 24px;
  --space-xl: 32px;

  /* 타이포그래피 */
  --font-title: 600 1.25rem/1.4 'Pretendard', sans-serif;
  --font-body: 400 0.938rem/1.6 'Pretendard', sans-serif;
  --font-caption: 400 0.813rem/1.5 'Pretendard', sans-serif;

  /* 그림자 */
  --shadow-card: 0 1px 3px rgba(0, 0, 0, 0.06);
  --shadow-modal: 0 4px 24px rgba(0, 0, 0, 0.12);

  /* 라운드 */
  --radius-sm: 8px;
  --radius-md: 12px;
  --radius-lg: 16px;
}
```

**수용 기준:**

- [ ] 워크플로우 단계가 별도 페이지/탭으로 분리된다
- [ ] 문의 상세 화면이 탭 기반으로 구성된다
- [ ] 공통 UI 컴포넌트가 5개 이상 추출되어 재사용된다
- [ ] 1280px 데스크톱 + 768px 태블릿에서 레이아웃이 정상 동작한다
- [ ] 신규 사용자가 첫 문의 작성을 10분 이내에 완료할 수 있다

---

## 4. 기술 영향도 분석

### 4.1 백엔드 변경 사항

| 영역 | 변경 내용 | 영향도 |
|------|----------|--------|
| **Knowledge Base** | 새 도메인 모듈 신설 (KnowledgeDocument 엔티티, Repository, Service, Controller) | 신규 |
| **Knowledge Base** | DocumentIndexingService 재사용 (기존 파싱/청킹/벡터화 파이프라인) | 수정 |
| **Knowledge Base** | VectorStore 검색 시 source 구분 메타데이터 추가 | 수정 |
| **Knowledge Base** | Flyway 마이그레이션 추가 (V13__knowledge_documents.sql) | 신규 |
| **문의 목록** | InquiryController에 `GET /inquiries` 추가 | 신규 |
| **문의 목록** | InquiryJpaRepository에 페이징 + 필터 쿼리 추가 | 수정 |
| **한국어화** | API 스키마 변경 없음 | 없음 |
| **UI/UX** | 기존 API 그대로 사용, CORS origin 추가만 가능성 있음 | 없음 |

### 4.2 프론트엔드 변경 사항

| 영역 | 변경 내용 | 영향도 |
|------|----------|--------|
| **Knowledge Base** | `/knowledge-base` 페이지 신규 생성 | 신규 |
| **문의 목록** | `/inquiries` 목록 페이지 신규 생성 | 신규 |
| **문의 상세** | `/inquiries/{id}` 탭 기반 상세 페이지 신규 생성 | 신규 |
| **한국어화** | `src/lib/i18n/labels.ts` 라벨 매핑 모듈 신규 | 신규 |
| **한국어화** | 기존 컴포넌트의 영문 표시 부분을 라벨 함수로 교체 | 수정 |
| **UI/UX** | `src/components/ui/*` 공통 컴포넌트 추출 | 신규 |
| **UI/UX** | 네비게이션 구조 확장 (4개 메뉴) | 수정 |
| **UI/UX** | `inquiry-form.tsx` 분해 → 탭별 컴포넌트 분리 | 리팩토링 |

### 4.3 데이터베이스 변경 사항

| 변경 | 마이그레이션 |
|------|------------|
| `knowledge_documents` 테이블 신규 | V13__knowledge_documents.sql |
| `knowledge_document_chunks` 테이블 신규 (또는 기존 document_chunks 재사용) | V13에 포함 |
| `inquiries` 테이블 인덱스 추가 (status, channel, keyword 검색용) | V14__inquiry_search_indexes.sql |

---

## 5. 실행 계획

### 5.1 스프린트 구성 (2주 사이클)

#### Sprint 7: 한국어화 + 문의 목록 (P2, P3)

> 난이도 낮고 즉각적인 사용성 개선이 큰 항목 우선

**Week 1:**
| ID | 작업 | 담당 | 우선순위 |
|----|------|------|---------|
| S7-01 | 한국어 라벨 매핑 모듈 구현 (`labels.ts`) | FE | P0 |
| S7-02 | 기존 화면에 라벨 매핑 적용 (판정/상태/리스크/에러) | FE | P0 |
| S7-03 | `GET /api/v1/inquiries` 목록 API 구현 (페이징/필터/정렬) | BE | P0 |
| S7-04 | `InquiryJpaRepository` 페이징 쿼리 추가 | BE | P0 |

**Week 2:**
| ID | 작업 | 담당 | 우선순위 |
|----|------|------|---------|
| S7-05 | `/inquiries` 목록 페이지 UI 구현 (테이블+페이징+필터) | FE | P0 |
| S7-06 | `/inquiries/{id}` 상세 페이지 골격 (탭 레이아웃) | FE | P0 |
| S7-07 | 네비게이션에 "문의 목록" 메뉴 추가 | FE | P0 |
| S7-08 | 한국어화 + 목록 기능 통합 테스트 | QA | P0 |

**Sprint 7 수용 기준:**
- 모든 판정·상태·플래그가 한국어로 표시
- 문의 목록이 20건 단위로 페이징되어 조회
- 목록에서 상세 페이지로 네비게이션 정상 동작

---

#### Sprint 8: 지식 기반 관리 (P1)

> 핵심 가치 제공 — CS 담당자가 참조 문서를 자율 관리

**Week 1:**
| ID | 작업 | 담당 | 우선순위 |
|----|------|------|---------|
| S8-01 | `knowledge_documents` 테이블 마이그레이션 | BE | P0 |
| S8-02 | KnowledgeDocument 엔티티 + Repository + Service | BE | P0 |
| S8-03 | Knowledge Base CRUD API 구현 | BE | P0 |
| S8-04 | 기존 인덱싱 파이프라인을 Knowledge Base에서도 사용 가능하도록 리팩토링 | BE | P0 |

**Week 2:**
| ID | 작업 | 담당 | 우선순위 |
|----|------|------|---------|
| S8-05 | 벡터 검색 통합 (inquiry chunk + KB chunk 동시 검색) | BE | P0 |
| S8-06 | EvidenceItem에 `sourceType` 필드 추가 | BE | P0 |
| S8-07 | `/knowledge-base` 관리 페이지 UI 구현 | FE | P0 |
| S8-08 | 분석 결과 UI에 출처 구분 표시 (문의 문서 / 지식 기반) | FE | P1 |
| S8-09 | Knowledge Base 통합 테스트 | QA | P0 |

**Sprint 8 수용 기준:**
- 문의 없이 기술 문서 등록 가능
- 등록된 문서가 인덱싱되어 벡터 검색에 포함
- 분석 결과에서 출처(문의 첨부 / 지식 기반) 구분 가능

---

#### Sprint 9: UI/UX 현대화 (P4)

> 전체 화면 구조 정리 + 디자인 시스템 적용

**Week 1:**
| ID | 작업 | 담당 | 우선순위 |
|----|------|------|---------|
| S9-01 | 공통 컴포넌트 추출 (Button, Badge, Card, DataTable, Toast, Tabs) | FE | P0 |
| S9-02 | 디자인 토큰 정리 (CSS 변수 체계화) | FE | P0 |
| S9-03 | `inquiry-form.tsx` 분해 → 탭별 컴포넌트 분리 | FE | P0 |
| S9-04 | `/inquiries/{id}` 탭 기반 상세 페이지 완성 | FE | P0 |

**Week 2:**
| ID | 작업 | 담당 | 우선순위 |
|----|------|------|---------|
| S9-05 | 대시보드 리디자인 (최근 문의 요약 추가) | FE | P1 |
| S9-06 | 반응형 레이아웃 적용 (1280px + 768px) | FE | P1 |
| S9-07 | 접근성 개선 (aria, 키보드 네비게이션, 포커스) | FE | P1 |
| S9-08 | 전체 화면 스모크 테스트 + 스크린샷 비교 | QA | P0 |

**Sprint 9 수용 기준:**
- 워크플로우 단계가 탭으로 분리
- 공통 컴포넌트 6개 이상 추출
- 태블릿 해상도에서 정상 렌더링
- 기존 기능 회귀 없음

---

### 5.2 전체 타임라인

```
Sprint 7 (2주)  ──── 한국어화 + 문의 목록 페이징
    ↓
Sprint 8 (2주)  ──── 지식 기반 관리 모듈
    ↓
Sprint 9 (2주)  ──── UI/UX 현대화 + 디자인 시스템
    ↓
Release v2.0    ──── 운영 배포
```

---

## 6. 리스크 및 대응

| 리스크 | 영향 | 확률 | 대응 |
|--------|------|------|------|
| Knowledge Base 벡터 통합 검색 시 성능 저하 | 검색 지연 | 중 | 인덱스 최적화 + 검색 결과 캐싱 + topK 제한 유지 |
| inquiry-form.tsx 분해 시 기능 회귀 | 기존 기능 장애 | 중 | 기존 통합 테스트 유지 + 분해 단위마다 스모크 테스트 |
| 한국어 라벨 누락 (새 상태/플래그 추가 시) | 영문 노출 | 저 | 매핑 없는 값은 원문 표시 (폴백) + 라벨 커버리지 테스트 |
| 대시보드 + 목록 동시 조회 시 DB 부하 | 응답 지연 | 저 | 인덱스 최적화 + 목록 쿼리 explain 분석 |

---

## 7. 성공 지표 (KPI)

| 지표 | 현재 | 목표 | 측정 방법 |
|------|------|------|----------|
| 화면 한국어 비율 | ~60% | 100% | 주요 화면 라벨 감사 |
| 문의 이력 검색 시간 | 불가능 | P95 ≤ 2초 | API 응답 시간 측정 |
| 지식 기반 문서 수 | 0건 | 운영 첫 주 10건+ | 등록 건수 모니터링 |
| 통합 검색 활용률 (KB 출처 포함 비율) | 0% | 50%+ | 분석 결과 sourceType 집계 |
| 신규 사용자 첫 작업 완료 시간 | 측정 없음 | 10분 이내 | 사용성 테스트 |
| 공통 컴포넌트 재사용율 | 0개 | 6개+ | 코드 리뷰 |

---

## 8. 의사결정 필요 사항

| # | 항목 | 선택지 | 권장 |
|---|------|--------|------|
| D1 | Knowledge Base 문서의 chunk를 기존 `document_chunks` 테이블에 통합할 것인가, 별도 테이블을 만들 것인가? | (A) 기존 테이블 재사용 + source 컬럼 추가 (B) 별도 `kb_document_chunks` 테이블 | **(A)** — 벡터 검색 통합이 간단하고 중복 코드 최소화 |
| D2 | 문의 상세 페이지 URL 구조 | (A) `/inquiries/{id}` (B) `/inquiry/{id}` | **(A)** — RESTful 복수형 통일 |
| D3 | UI 프레임워크 도입 여부 | (A) 순수 CSS + 공통 컴포넌트 (B) Tailwind CSS (C) shadcn/ui | 현재 종속성 최소 유지 우선 → **(A)**, 향후 필요 시 (B)로 전환 |
| D4 | 문의 목록 검색 시 전문 검색(Full-text) 필요 여부 | (A) LIKE 검색 (B) PostgreSQL tsvector | MVP는 **(A)**, 문의 1000건 초과 시 (B) 도입 |

---

## 9. 부록: 현재 시스템 vs 개선 후 비교

### 사용자 여정 비교

**AS-IS (현재):**
```
CS 담당자가 문의를 받음
  → /inquiry/new 접속
  → 질문 입력 + 고객 문서 첨부
  → 문의 ID 복사
  → 같은 페이지에서 ID 입력하여 조회
  → 인덱싱 실행
  → 분석 실행 (결과가 영어로 표시됨)
  → 초안 생성
  → 리뷰/승인/발송 (같은 페이지, 스크롤 다운)
  → 과거 문의 참조 불가 (ID를 기억해야 함)
  → Bio-Rad 기술 문서 참조 불가 (업로드할 곳 없음)
```

**TO-BE (개선 후):**
```
CS 담당자가 문의를 받음
  → /inquiries/new 에서 질문 입력 + 고객 문서 첨부
  → 자동으로 /inquiries/{id} 상세 페이지로 이동
  → [기본 정보] 탭: 문서 상태 확인 + 인덱싱 실행
  → [분석] 탭: 근거 검색 + 판정 결과 (한국어로 "근거 충분" 표시)
                근거 출처에 "지식 기반: Reagent X 매뉴얼" 표시
  → [답변] 탭: 초안 생성 → 리뷰 → 승인 → 발송
  → [이력] 탭: 버전 히스토리 + 감사 로그

  과거 문의 참조:
  → /inquiries 목록에서 키워드 검색 (예: "Reagent X")
  → 과거 대응 내역 즉시 확인

  지식 기반 관리:
  → /knowledge-base 에서 Bio-Rad 기술 문서 사전 등록
  → 모든 문의 분석 시 자동으로 참조됨
```

---

> **다음 단계:** 본 PRD 리뷰 → 의사결정 사항 확정 → Sprint 7 실행 백로그 작성
