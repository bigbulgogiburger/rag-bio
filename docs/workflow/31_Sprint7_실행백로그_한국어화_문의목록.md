# 31. Sprint 7 실행백로그 — 한국어화 + 문의 목록 페이징

> 상태: **완료 (Sprint7 산출물 반영됨)**

## 목표

1. 화면에 표시되는 모든 영문 코드(판정·상태·리스크 플래그·에러)를 **한국어 라벨**로 전환
2. **문의 목록 조회 API** 신규 구현 (페이징 + 필터 + 정렬)
3. `/inquiries` **목록 페이지** + `/inquiries/{id}` **상세 페이지(탭 골격)** 신규 구현
4. 네비게이션 확장 (기존 2개 → 4개 메뉴)

---

## 운영 원칙

1. **API 스키마 하위 호환**: 백엔드 Enum 값은 영문 유지, 프론트엔드 표시 시점에 한국어 변환
2. **기존 기능 회귀 방지**: 기존 통합 테스트(`AnswerWorkflowIntegrationTest` 등) 통과 필수
3. **점진적 페이지 분리**: inquiry-form.tsx 내부 코드를 이번 스프린트에서 상세 페이지로 이관 시작

---

## FE-01. 한국어 라벨 매핑 모듈 (P0)

### 목적

프론트엔드에서 API 응답의 영문 값을 한국어 UI 라벨로 변환하는 **단일 소스 매핑 모듈**을 만든다.

### 작업 내용

**신규 파일 생성:** `frontend/src/lib/i18n/labels.ts`

```typescript
// ===== 판정 (Verdict) =====
export const VERDICT_LABELS: Record<string, string> = {
  SUPPORTED:   "근거 충분",
  REFUTED:     "근거 부족",
  CONDITIONAL: "조건부",
};

// ===== 답변 상태 (Answer Status) =====
export const ANSWER_STATUS_LABELS: Record<string, string> = {
  DRAFT:    "초안",
  REVIEWED: "검토 완료",
  APPROVED: "승인 완료",
  SENT:     "발송 완료",
};

// ===== 문서 처리 상태 (Document Status) =====
export const DOC_STATUS_LABELS: Record<string, string> = {
  UPLOADED:       "업로드됨",
  PARSING:        "파싱 중",
  PARSED:         "파싱 완료",
  PARSED_OCR:     "OCR 파싱 완료",
  CHUNKED:        "청크 완료",
  INDEXED:        "인덱싱 완료",
  FAILED_PARSING: "파싱 실패",
};

// ===== 문의 상태 (Inquiry Status) =====
export const INQUIRY_STATUS_LABELS: Record<string, string> = {
  RECEIVED: "접수됨",
  ANALYZED: "분석 완료",
  ANSWERED: "답변 생성됨",
  CLOSED:   "종료",
};

// ===== 리스크 플래그 =====
export const RISK_FLAG_LABELS: Record<string, string> = {
  LOW_CONFIDENCE:         "신뢰도 낮음",
  WEAK_EVIDENCE_MATCH:    "근거 약함",
  CONFLICTING_EVIDENCE:   "근거 상충",
  INSUFFICIENT_EVIDENCE:  "근거 부족",
  FALLBACK_DRAFT_USED:    "대체 초안 사용됨",
  ORCHESTRATION_FALLBACK: "처리 중 오류 발생",
};

// ===== 톤 =====
export const TONE_LABELS: Record<string, string> = {
  professional: "정중체",
  technical:    "기술 상세",
  brief:        "요약",
};

// ===== 채널 =====
export const CHANNEL_LABELS: Record<string, string> = {
  email:     "이메일",
  messenger: "메신저",
  portal:    "포털",
};

// ===== 에러 코드 =====
export const ERROR_LABELS: Record<string, string> = {
  AUTH_USER_ID_REQUIRED: "사용자 ID가 필요합니다",
  AUTH_ROLE_FORBIDDEN:   "권한이 부족합니다",
  INVALID_STATE:         "현재 상태에서는 수행할 수 없습니다",
  NOT_FOUND:             "요청한 항목을 찾을 수 없습니다",
  CONFLICT:              "이미 처리된 요청입니다",
};

// ===== 공통 변환 함수 =====
export function label(map: Record<string, string>, key: string): string {
  return map[key] ?? key;           // 매핑 없으면 원문 그대로 표시 (안전 장치)
}

export function labelVerdict(v: string): string { return label(VERDICT_LABELS, v); }
export function labelAnswerStatus(s: string): string { return label(ANSWER_STATUS_LABELS, s); }
export function labelDocStatus(s: string): string { return label(DOC_STATUS_LABELS, s); }
export function labelInquiryStatus(s: string): string { return label(INQUIRY_STATUS_LABELS, s); }
export function labelRiskFlag(f: string): string { return label(RISK_FLAG_LABELS, f); }
export function labelTone(t: string): string { return label(TONE_LABELS, t); }
export function labelChannel(c: string): string { return label(CHANNEL_LABELS, c); }
```

### 수용 기준

- [ ] 모든 라벨 함수가 해당 키에 대해 한국어를 반환한다
- [ ] 매핑에 없는 키를 전달하면 원문이 그대로 반환된다 (에러 없음)

---

## FE-02. 기존 화면 한국어 라벨 적용 (P0)

### 목적

`inquiry-form.tsx`와 `dashboard/page.tsx`에서 영문으로 표시되는 모든 값을 FE-01의 라벨 함수로 교체한다.

### 작업 내용

**수정 파일:** `frontend/src/components/inquiry-form.tsx`

#### 교체 대상 목록

| 현재 코드 위치 | 현재 표시 | 교체 함수 |
|---------------|----------|----------|
| 판정 결과 `analysisResult.verdict` | `SUPPORTED` | `labelVerdict(analysisResult.verdict)` |
| 답변 상태 `draft.status` | `DRAFT` | `labelAnswerStatus(draft.status)` |
| 타임라인 단계명 `DRAFT/REVIEWED/APPROVED/SENT` | 영문 | `labelAnswerStatus(step)` |
| 문서 상태 배지 `doc.status` | `UPLOADED` | `labelDocStatus(doc.status)` |
| 리스크 플래그 `flag` | `LOW_CONFIDENCE` | `labelRiskFlag(flag)` |
| 톤 표시 `draft.tone` | `professional` | `labelTone(draft.tone)` |
| 채널 표시 `draft.channel` | `email` | `labelChannel(draft.channel)` |

#### 기존 인라인 매핑 함수 제거

현재 `inquiry-form.tsx` 내부에 있는 `mapStatusLabel()`, `mapVerdictLabel()` 등 인라인 헬퍼를 **삭제**하고 `labels.ts`의 함수로 대체한다.

```typescript
// 삭제 대상 (inquiry-form.tsx 내부)
// function mapStatusLabel(status: string): string { ... }
// function mapVerdictLabel(verdict: string): string { ... }
// function badgeClassByStatus(status: string): string { ... }  ← 이건 유지 (CSS 클래스 매핑)

// 교체
import { labelVerdict, labelAnswerStatus, labelDocStatus, labelRiskFlag, labelTone, labelChannel } from '@/lib/i18n/labels';
```

#### 에러 메시지 한국어화

API 호출 `catch` 블록에서 에러 메시지를 한국어로 변환:

```typescript
import { ERROR_LABELS, label } from '@/lib/i18n/labels';

// 기존: setMsg(err.message)
// 변경: setMsg(label(ERROR_LABELS, err.code) || err.message)
```

**수정 파일:** `frontend/src/app/dashboard/page.tsx`

- 대시보드 카드 제목은 이미 한국어 → 변경 불필요
- `topFailureReasons`의 `reason` 필드가 영문일 경우 라벨 매핑 적용

### 수용 기준

- [ ] 판정 결과가 "근거 충분" / "근거 부족" / "조건부"로 표시된다
- [ ] 문서 상태가 "업로드됨" ~ "인덱싱 완료"로 표시된다
- [ ] 답변 상태가 "초안" ~ "발송 완료"로 표시된다
- [ ] 리스크 플래그가 한국어로 표시된다
- [ ] 타임라인 단계명이 한국어로 표시된다
- [ ] 기존 기능(문의 등록 → 인덱싱 → 분석 → 초안 → 승인 → 발송) 회귀 없음

---

## BE-01. 문의 목록 조회 API (P0)

### 목적

문의 전체 목록을 **페이징 + 필터 + 정렬**로 조회하는 엔드포인트를 신규 구현한다.

### API 스펙

```
GET /api/v1/inquiries
```

#### 요청 파라미터 (Query String)

| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|-------|------|
| `page` | int | 0 | 페이지 번호 (0-based) |
| `size` | int | 20 | 페이지 크기 (최대 100) |
| `sort` | string | `createdAt,desc` | 정렬 기준 (`createdAt,asc` 또는 `createdAt,desc`) |
| `status` | string | (없음) | 문의 상태 필터 (CSV: `RECEIVED,ANALYZED`) |
| `channel` | string | (없음) | 채널 필터 (`email`, `messenger`, `portal`) |
| `keyword` | string | (없음) | 질문 내용 키워드 검색 (LIKE `%keyword%`) |
| `from` | string | (없음) | 시작일 (ISO-8601: `2026-01-01T00:00:00Z`) |
| `to` | string | (없음) | 종료일 (ISO-8601: `2026-02-13T23:59:59Z`) |

#### 응답 (200 OK)

```json
{
  "content": [
    {
      "inquiryId": "550e8400-e29b-41d4-a716-446655440000",
      "question": "Reagent X를 4도에서 야간 보관해도 되나요?",
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

#### 필드 상세

| 필드 | 설명 |
|------|------|
| `question` | 최대 200자 (초과 시 말줄임 처리) |
| `documentCount` | 해당 문의에 첨부된 문서 수 |
| `latestAnswerStatus` | 가장 최근 답변 초안의 상태 (`null`이면 답변 미생성) |

### 백엔드 구현 가이드

#### 1단계: DTO 생성

**신규 파일:** `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/dto/InquiryListResponse.java`

```java
package com.biorad.csrag.interfaces.rest.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InquiryListResponse(
    List<InquiryListItem> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public record InquiryListItem(
        UUID inquiryId,
        String question,
        String customerChannel,
        String status,
        int documentCount,
        String latestAnswerStatus,  // nullable
        Instant createdAt
    ) {}
}
```

#### 2단계: Repository 쿼리 추가

**수정 파일:** `backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/inquiry/InquiryJpaRepository.java`

Spring Data JPA `Specification` 기반 동적 쿼리를 사용한다:

```java
public interface InquiryJpaRepository extends JpaRepository<InquiryJpaEntity, UUID>,
                                              JpaSpecificationExecutor<InquiryJpaEntity> {
    // 기존 메서드 유지
}
```

**신규 파일:** `backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/inquiry/InquirySpecifications.java`

```java
package com.biorad.csrag.infrastructure.persistence.inquiry;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class InquirySpecifications {

    public static Specification<InquiryJpaEntity> withFilters(
            List<String> statuses,
            String channel,
            String keyword,
            Instant from,
            Instant to
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            if (channel != null && !channel.isBlank()) {
                predicates.add(cb.equal(root.get("customerChannel"), channel));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(
                    cb.lower(root.get("question")),
                    "%" + keyword.toLowerCase() + "%"
                ));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
```

#### 3단계: 서비스 계층

**신규 파일:** `backend/app-api/src/main/java/com/biorad/csrag/application/InquiryListService.java`

```java
package com.biorad.csrag.application;

import com.biorad.csrag.infrastructure.persistence.inquiry.*;
import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaRepository;
import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaRepository;
import com.biorad.csrag.interfaces.rest.dto.InquiryListResponse;
import com.biorad.csrag.interfaces.rest.dto.InquiryListResponse.InquiryListItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InquiryListService {

    private final InquiryJpaRepository inquiryRepo;
    private final DocumentMetadataJpaRepository documentRepo;
    private final AnswerDraftJpaRepository answerRepo;

    // constructor injection

    public InquiryListResponse list(
            List<String> statuses,
            String channel,
            String keyword,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        Page<InquiryJpaEntity> page = inquiryRepo.findAll(
            InquirySpecifications.withFilters(statuses, channel, keyword, from, to),
            pageable
        );

        List<InquiryListItem> items = page.getContent().stream()
            .map(entity -> {
                int docCount = documentRepo.countByInquiryId(entity.getId());
                String latestAnswerStatus = answerRepo
                    .findTopByInquiryIdOrderByVersionDesc(entity.getId())
                    .map(a -> a.getStatus())
                    .orElse(null);

                String questionSummary = entity.getQuestion().length() > 200
                    ? entity.getQuestion().substring(0, 200) + "…"
                    : entity.getQuestion();

                return new InquiryListItem(
                    entity.getId(),
                    questionSummary,
                    entity.getCustomerChannel(),
                    entity.getStatus().name(),
                    docCount,
                    latestAnswerStatus,
                    entity.getCreatedAt()
                );
            })
            .toList();

        return new InquiryListResponse(
            items,
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
```

#### 4단계: 컨트롤러 엔드포인트 추가

**수정 파일:** `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/InquiryController.java`

기존 `InquiryController`에 목록 조회 메서드 추가:

```java
@GetMapping
public ResponseEntity<InquiryListResponse> listInquiries(
        @RequestParam(required = false) List<String> status,
        @RequestParam(required = false) String channel,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Instant from,
        @RequestParam(required = false) Instant to,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
        Pageable pageable
) {
    InquiryListResponse response = inquiryListService.list(
        status, channel, keyword, from, to, pageable
    );
    return ResponseEntity.ok(response);
}
```

#### 5단계: Repository 카운트 메서드 추가

**수정 파일:** `DocumentMetadataJpaRepository.java`

```java
int countByInquiryId(UUID inquiryId);
```

### DB 변경

**신규 마이그레이션:** `V14__inquiry_search_indexes.sql`

```sql
CREATE INDEX IF NOT EXISTS idx_inquiries_status ON inquiries(status);
CREATE INDEX IF NOT EXISTS idx_inquiries_channel ON inquiries(customer_channel);
CREATE INDEX IF NOT EXISTS idx_inquiries_question_lower ON inquiries(LOWER(question));
```

> **주의:** PostgreSQL에서 `LOWER()` 인덱스는 `CREATE INDEX ... ON (LOWER(column))` 형식의 표현식 인덱스이다.
> H2 호환성을 위해 실행 실패 시 무시하도록 처리하거나, Docker 프로파일에서만 적용한다.

### 테스트

**신규 파일:** `InquiryListServiceTest.java` (단위 테스트)

```
- 필터 없이 전체 조회 → 페이징 정상
- status 필터 적용 → 해당 상태만 반환
- keyword 검색 → 질문에 키워드 포함된 건만 반환
- from/to 기간 필터 → 범위 내 건만 반환
- 복합 필터 (status + channel + keyword) → AND 조건 정상
- 빈 결과 → content: [], totalElements: 0
- question 200자 초과 → 말줄임 처리 확인
```

**신규 파일:** `InquiryControllerWebMvcTest.java` (컨트롤러 테스트)

```
- GET /api/v1/inquiries → 200 + 페이징 응답
- page/size 파라미터 정상 동작
- 잘못된 파라미터 → 400
```

### 수용 기준

- [ ] `GET /api/v1/inquiries` 엔드포인트가 페이징된 결과를 반환한다
- [ ] status, channel, keyword, from, to 필터가 정상 동작한다
- [ ] 정렬(createdAt asc/desc)이 정상 동작한다
- [ ] documentCount와 latestAnswerStatus가 정확히 반환된다
- [ ] 기존 `POST /api/v1/inquiries`, `GET /api/v1/inquiries/{id}` 회귀 없음

---

## FE-03. 문의 목록 페이지 (P0)

### 목적

문의 대응 내역을 **검색·필터·페이징**으로 조회하는 목록 페이지를 구현한다.

### 작업 내용

#### 1. API 클라이언트 함수 추가

**수정 파일:** `frontend/src/lib/api/client.ts`

```typescript
// ===== 신규 타입 =====
export interface InquiryListItem {
  inquiryId: string;
  question: string;
  customerChannel: string;
  status: string;
  documentCount: number;
  latestAnswerStatus: string | null;
  createdAt: string;
}

export interface InquiryListResponse {
  content: InquiryListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface InquiryListParams {
  page?: number;
  size?: number;
  sort?: string;
  status?: string[];
  channel?: string;
  keyword?: string;
  from?: string;
  to?: string;
}

// ===== 신규 함수 =====
export async function listInquiries(params: InquiryListParams = {}): Promise<InquiryListResponse> {
  const query = new URLSearchParams();
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));
  if (params.sort) query.set("sort", params.sort);
  if (params.status?.length) params.status.forEach(s => query.append("status", s));
  if (params.channel) query.set("channel", params.channel);
  if (params.keyword) query.set("keyword", params.keyword);
  if (params.from) query.set("from", params.from);
  if (params.to) query.set("to", params.to);

  const res = await fetch(`${BASE}/api/v1/inquiries?${query.toString()}`);
  if (!res.ok) throw new Error(`목록 조회 실패: ${res.status}`);
  return res.json();
}
```

#### 2. 목록 페이지 구현

**신규 파일:** `frontend/src/app/inquiries/page.tsx`

**화면 구조:**

```
┌─────────────────────────────────────────────────────────┐
│ 문의 대응 내역                                    [문의 작성 →] │
├─────────────────────────────────────────────────────────┤
│ [상태 ▼] [채널 ▼] [시작일 📅] ~ [종료일 📅] [검색어____] [검색] │
├──────┬──────────────────┬──────┬──────┬──────┬──────────┤
│ 접수일 │ 질문 요약          │ 채널  │ 상태  │ 답변  │ 문서 수  │
├──────┼──────────────────┼──────┼──────┼──────┼──────────┤
│02-13 │ Reagent X 보관...  │이메일 │접수됨 │승인완료│    3    │
│02-12 │ Protocol Y 유효... │메신저 │분석완료│ 초안  │    1    │
│ ...  │ ...              │ ...  │ ...  │ ...  │   ...   │
├──────┴──────────────────┴──────┴──────┴──────┴──────────┤
│ 전체 142건 중 1-20건                                      │
│ [◀ 이전] [1] [2] [3] ... [8] [다음 ▶]     [20건 ▼]       │
└─────────────────────────────────────────────────────────┘
```

**주요 구현 사항:**

| 항목 | 상세 |
|------|------|
| 상태 필터 | 다중 선택 가능: 접수됨, 분석 완료, 답변 생성됨, 종료 |
| 채널 필터 | 단일 선택: 이메일, 메신저, 포털 |
| 기간 필터 | `<input type="date">` 2개 (시작일, 종료일) |
| 키워드 검색 | 질문 내용 텍스트 검색 |
| 테이블 행 클릭 | `router.push(\`/inquiries/${item.inquiryId}\`)` |
| 페이징 | 페이지 번호 버튼 + 이전/다음 |
| 건수 선택 | 20건 / 50건 / 100건 드롭다운 |
| 상태 배지 | `labelInquiryStatus()`, `labelAnswerStatus()` 사용 |
| 채널 표시 | `labelChannel()` 사용 |
| 빈 상태 | "등록된 문의가 없습니다" 안내 메시지 |
| 로딩 | 스피너 표시 |

### 수용 기준

- [ ] `/inquiries` 페이지에서 문의 목록이 20건 단위로 표시된다
- [ ] 필터 적용 후 [검색] 버튼 클릭 시 결과가 갱신된다
- [ ] 페이지 번호 클릭 시 해당 페이지로 이동한다
- [ ] 행 클릭 시 `/inquiries/{id}` 상세 페이지로 이동한다
- [ ] 모든 상태·채널이 한국어로 표시된다

---

## FE-04. 문의 상세 페이지 골격 (P0)

### 목적

기존 `/inquiry/new`에 밀집된 워크플로우를 **탭 기반 상세 페이지**로 분리한다.
이번 스프린트에서는 **탭 골격 + 기본 정보 탭**만 구현하고, 나머지 탭은 Sprint 9에서 완성한다.

### 작업 내용

**신규 파일:** `frontend/src/app/inquiries/[id]/page.tsx`

**화면 구조:**

```
┌─────────────────────────────────────────────────────────┐
│ ← 목록으로   문의 상세 — INQ-550e8400                       │
├──────────┬──────────┬──────────┬──────────────────────────┤
│ 기본 정보 │  분석    │  답변    │  이력                      │
├──────────┴──────────┴──────────┴──────────────────────────┤
│                                                          │
│  [기본 정보 탭 내용]                                        │
│                                                          │
│  ┌── 문의 정보 ──────────────────────┐                     │
│  │ 질문: Reagent X를 4도에서...       │                     │
│  │ 채널: 이메일                       │                     │
│  │ 상태: 접수됨                       │                     │
│  │ 접수일: 2026-02-13 12:00          │                     │
│  └──────────────────────────────────┘                     │
│                                                          │
│  ┌── 첨부 문서 (3건) ────────────────┐                     │
│  │ 파일명    │ 상태      │ 청크  │ 벡터 │                    │
│  │ doc1.pdf │ 인덱싱완료 │  12  │  12  │                    │
│  │ doc2.docx│ 파싱 중   │  -   │  -   │                    │
│  │ doc3.pdf │ 파싱 실패 │  -   │  -   │                    │
│  └──────────────────────────────────┘                     │
│                                                          │
│  [인덱싱 실행]  [실패 건 재처리]                              │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

**탭 구현:**

| 탭 | Sprint 7 | 내용 |
|----|----------|------|
| 기본 정보 | 구현 | 문의 정보 카드 + 문서 목록 + 인덱싱 상태/실행 |
| 분석 | 빈 탭 표시 | "분석 기능은 준비 중입니다" (Sprint 9에서 구현) |
| 답변 | 빈 탭 표시 | "답변 기능은 준비 중입니다" (Sprint 9에서 구현) |
| 이력 | 빈 탭 표시 | "이력 기능은 준비 중입니다" (Sprint 9에서 구현) |

**기본 정보 탭:**
- 기존 `inquiry-form.tsx`의 조회/인덱싱 부분 코드를 이관
- `getInquiry()`, `listInquiryDocuments()`, `getInquiryIndexingStatus()`, `runInquiryIndexing()` API 사용
- 문서 상태 배지에 `labelDocStatus()` 적용

### 수용 기준

- [ ] `/inquiries/{id}` 경로로 접근 시 탭 레이아웃이 표시된다
- [ ] 기본 정보 탭에서 문의 정보 + 문서 목록이 표시된다
- [ ] 인덱싱 실행/재처리 버튼이 정상 동작한다
- [ ] 모든 상태가 한국어 라벨로 표시된다
- [ ] "← 목록으로" 클릭 시 `/inquiries` 페이지로 돌아간다

---

## FE-05. 네비게이션 확장 (P0)

### 목적

상단 네비게이션에 새 메뉴를 추가하고, 문의 작성 경로를 `/inquiries/new`로 변경한다.

### 작업 내용

**수정 파일:** `frontend/src/components/app-shell-nav.tsx`

**변경 전:**
```
대시보드 (/dashboard)  |  문의 작성 (/inquiry/new)
```

**변경 후:**
```
대시보드 (/dashboard)  |  문의 목록 (/inquiries)  |  문의 작성 (/inquiries/new)  |  지식 기반 (/knowledge-base)
```

> **참고:** "지식 기반" 메뉴는 Sprint 8에서 구현. 이번 스프린트에서는 링크만 추가하고 페이지는 "준비 중" placeholder로 둔다.

**신규 파일:** `frontend/src/app/inquiries/new/page.tsx`

기존 `/inquiry/new/page.tsx`에서 **문의 생성 폼 부분만** 이관한다.
(조회/분석/초안 부분은 상세 페이지로 이동했으므로)

**기존 경로 리다이렉트:** `frontend/src/app/inquiry/new/page.tsx`

```typescript
import { redirect } from 'next/navigation';
export default function Page() { redirect('/inquiries/new'); }
```

### 수용 기준

- [ ] 네비게이션에 4개 메뉴가 표시된다
- [ ] 현재 페이지에 해당하는 메뉴가 활성화(active) 상태로 표시된다
- [ ] 기존 `/inquiry/new` URL이 `/inquiries/new`로 리다이렉트된다

---

## FE-06. 지식 기반 placeholder 페이지 (P1)

### 작업 내용

**신규 파일:** `frontend/src/app/knowledge-base/page.tsx`

```
"지식 기반 관리 기능은 다음 스프린트에서 제공됩니다."
```

Sprint 8에서 실제 기능으로 교체 예정.

---

## 실행 순서

```
Week 1:
  1) FE-01  한국어 라벨 모듈 (FE, 0.5일)
  2) FE-02  기존 화면 라벨 적용 (FE, 1일)
  3) BE-01  문의 목록 API 구현 (BE, 2일)
     ├── DTO + Specification + Service + Controller
     ├── DB 마이그레이션 (V14)
     └── 단위 테스트 + WebMvc 테스트

Week 2:
  4) FE-03  문의 목록 페이지 UI (FE, 2일)
  5) FE-04  문의 상세 페이지 골격 (FE, 2일)
  6) FE-05  네비게이션 확장 (FE, 0.5일)
  7) FE-06  지식 기반 placeholder (FE, 0.5일)
  8) QA     한국어 + 목록 + 상세 통합 검증 (1일)
```

---

## 수용 기준 (Sprint 전체)

1. 화면에 표시되는 모든 판정·상태·리스크·채널·톤이 한국어이다
2. `GET /api/v1/inquiries`가 페이징·필터·정렬된 결과를 반환한다
3. `/inquiries` 페이지에서 목록 조회 → 행 클릭 → 상세 페이지 이동이 정상 동작한다
4. `/inquiries/{id}` 탭 기반 상세 페이지에서 기본 정보 탭이 정상 동작한다
5. 네비게이션에 4개 메뉴가 표시된다
6. 기존 통합 테스트(`AnswerWorkflowIntegrationTest` 등) 전체 통과
7. `./gradlew build` + `npm run build` 모두 성공
