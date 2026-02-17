# PRD: Knowledge Base 비동기 인덱싱

> **버전**: v1.0
> **작성일**: 2026-02-15
> **상태**: Draft

---

## 1. 배경 및 문제

현재 KB 문서 인덱싱 API(`POST /documents/{docId}/indexing/run`)는 **동기 방식**으로 동작한다.
텍스트 추출 → 청킹 → OpenAI 임베딩 → Qdrant upsert를 모두 완료한 뒤에야 HTTP 응답을 반환한다.

**문제점:**
- 4.5MB PDF 기준 **3~7분** 소요 (청크당 ~350ms × 수백~천 건의 OpenAI API 호출)
- 프론트엔드에서 HTTP 요청이 타임아웃되거나 사용자가 브라우저 탭을 닫으면 인덱싱 상태가 꼬임
- 사용자가 인덱싱 완료까지 화면에서 아무것도 못 하고 대기해야 함
- 일괄 인덱싱 시 문서 수 × 단건 시간만큼 블로킹

---

## 2. 목표

1. 인덱싱 API 호출 즉시 `202 Accepted` 반환, 백그라운드에서 비동기 처리
2. 상태 머신을 4단계로 단순화하여 사용자가 직관적으로 파악 가능
3. 프론트엔드에서 폴링 기반 진행률 표시 (페이지를 떠나도 인덱싱 계속 진행)

---

## 3. 상태 머신 (State Machine)

### 3.1 상태 정의

| 상태 | 영문 코드 | 설명 |
|------|----------|------|
| 업로드됨 | `UPLOADED` | 파일 저장 완료, 인덱싱 미실행 |
| 인덱싱 중 | `INDEXING` | 비동기 인덱싱 진행 중 (텍스트 추출 → 청킹 → 벡터화) |
| 인덱싱 완료 | `INDEXED` | 청크 + 벡터 저장 완료 |
| 인덱싱 실패 | `FAILED` | 인덱싱 도중 에러 발생 (재시도 가능) |

### 3.2 상태 전이

```
UPLOADED ──(인덱싱 요청)──→ INDEXING
INDEXING ──(성공)──────────→ INDEXED
INDEXING ──(실패)──────────→ FAILED
FAILED   ──(재시도 요청)──→ INDEXING
```

### 3.3 기존 상태와 매핑

기존에 세분화된 중간 상태(`PARSING`, `PARSED`, `PARSED_OCR`, `CHUNKED`, `FAILED_PARSING`)는 백엔드 내부 로그용으로만 남기고, **프론트엔드에 노출하는 상태는 4가지로 통합**한다.

| 기존 상태 | 신규 상태 |
|----------|----------|
| `UPLOADED` | `UPLOADED` |
| `PARSING`, `PARSED`, `PARSED_OCR`, `CHUNKED` | `INDEXING` |
| `INDEXED` | `INDEXED` |
| `FAILED`, `FAILED_PARSING` | `FAILED` |

---

## 4. 백엔드 변경사항

### 4.1 비동기 처리

- `KnowledgeBaseService.indexOne()` 호출을 `@Async` 메서드로 분리
- Spring `@EnableAsync` 활성화, 전용 ThreadPool 설정 (core=2, max=4, queue=50)
- 인덱싱 요청 시 즉시 상태를 `INDEXING`으로 변경 후 `202 Accepted` 반환
- 백그라운드 스레드에서 텍스트 추출 → 청킹 → 벡터화 수행
- 성공 시 `INDEXED`, 실패 시 `FAILED` + `lastError` 기록

### 4.2 API 변경

#### 개별 인덱싱 (변경)
```
POST /api/v1/knowledge-base/documents/{docId}/indexing/run

현재: 200 OK + { documentId, status, chunkCount, vectorCount }  (동기, 수 분 블로킹)
변경: 202 Accepted + { documentId, status: "INDEXING", message: "인덱싱이 시작되었습니다." }
```

#### 일괄 인덱싱 (변경)
```
POST /api/v1/knowledge-base/indexing/run

현재: 200 OK + { processed, succeeded, failed }  (동기, N × 단건 시간 블로킹)
변경: 202 Accepted + { queued: N, message: "N건의 인덱싱이 시작되었습니다." }
```

#### 상태 조회 (기존 활용)
```
GET /api/v1/knowledge-base/documents/{docId}

→ status, chunkCount, vectorCount, lastError 필드로 진행 상황 확인
```

### 4.3 이중 실행 방지

- `INDEXING` 상태인 문서에 대해 인덱싱 재요청 시 `409 Conflict` 반환
- 동일 문서의 중복 비동기 작업 방지

### 4.4 진행률 (선택사항, 후속 스프린트)

- `document_chunks` 테이블의 벡터화된 청크 수 / 전체 청크 수로 진행률 계산 가능
- 현재 스프린트에서는 상태 4단계만 구현, 퍼센트 진행률은 후속 고도화

---

## 5. 프론트엔드 변경사항

### 5.1 상태 배지 단순화

```typescript
// labels.ts 변경
export const DOC_STATUS_LABELS: Record<string, string> = {
  UPLOADED: "업로드됨",
  INDEXING: "인덱싱 중",
  INDEXED: "인덱싱 완료",
  FAILED: "인덱싱 실패",
};
```

### 5.2 인덱싱 요청 UX 변경

**현재**: 인덱싱 버튼 클릭 → 로딩 스피너 → (수 분 대기) → 완료/실패 알림
**변경**: 인덱싱 버튼 클릭 → 즉시 토스트 "인덱싱이 시작되었습니다" → 상태 배지 `인덱싱 중` 표시

### 5.3 폴링 기반 상태 갱신

- `INDEXING` 상태 문서가 1건 이상이면 **5초 간격 폴링** 시작 (문서 목록 API 재호출)
- 모든 문서가 `INDEXED` 또는 `FAILED`가 되면 폴링 중단
- 폴링 중 `인덱싱 중` 배지 옆에 **스피너 아이콘** 표시

### 5.4 인덱싱 중 배지 스타일

| 상태 | 배지 variant | 아이콘 |
|------|-------------|--------|
| 업로드됨 | `neutral` | - |
| 인덱싱 중 | `warn` | 스피너 (CSS animation) |
| 인덱싱 완료 | `success` | - |
| 인덱싱 실패 | `danger` | - |

### 5.5 실패 시 재시도

- `FAILED` 상태 문서에 "재시도" 버튼 표시
- 클릭 시 동일한 비동기 인덱싱 요청 → `INDEXING` 전환

---

## 6. 구현 범위

### Sprint 12 (이번 스프린트)

| # | 작업 | 레이어 | 예상 난이도 |
|---|------|--------|-----------|
| 1 | `@EnableAsync` + ThreadPool 설정 | Backend | 낮음 |
| 2 | `KnowledgeBaseService` 비동기 분리 (`indexOneAsync`) | Backend | 중간 |
| 3 | 상태 머신 단순화 (`UPLOADED → INDEXING → INDEXED/FAILED`) | Backend | 낮음 |
| 4 | API 응답 `202 Accepted` + 이중 실행 방지 | Backend | 낮음 |
| 5 | `labels.ts` 상태 레이블 업데이트 | Frontend | 낮음 |
| 6 | 인덱싱 버튼 UX 변경 (즉시 토스트 + 비동기) | Frontend | 낮음 |
| 7 | 폴링 기반 상태 갱신 (5초 간격) | Frontend | 중간 |
| 8 | `INDEXING` 배지 스피너 CSS | Frontend | 낮음 |

### 후속 고도화 (선택)

- 퍼센트 진행률 표시 (청크 수 기반)
- WebSocket/SSE 기반 실시간 상태 푸시
- 인덱싱 큐 관리 UI (우선순위, 취소)

---

## 7. 비기능 요구사항

- **동시 인덱싱**: 최대 4건 병렬 처리 (ThreadPool max=4)
- **큐 초과 시**: 50건 초과 요청은 `503 Service Unavailable` + "인덱싱 큐가 가득 찼습니다" 메시지
- **타임아웃**: 단건 인덱싱 30분 초과 시 자동 `FAILED` 처리
- **멱등성**: 동일 문서 중복 요청 시 409 반환, 데이터 무결성 보장

---

## 8. 테스트 계획

| 시나리오 | 검증 항목 |
|---------|----------|
| 단건 인덱싱 요청 | 202 반환, 상태 INDEXING → INDEXED 전이 확인 |
| 인덱싱 중 재요청 | 409 Conflict 반환 |
| OpenAI 503 장애 | FAILED 전이 + lastError 기록 |
| 일괄 인덱싱 (3건) | 202 반환, 3건 모두 비동기 처리 |
| 프론트 폴링 | INDEXING 동안 5초 간격 갱신, 완료 시 폴링 중단 |
| 브라우저 닫기 | 인덱싱 계속 진행, 재접속 시 최신 상태 반영 |
| FAILED 재시도 | FAILED → INDEXING → INDEXED 전이 |
