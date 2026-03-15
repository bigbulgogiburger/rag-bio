---
name: verify-api-contracts
description: REST API 응답 DTO 일관성 및 에러 코드 표준 검증. API 엔드포인트 추가/수정, DTO 변경, 에러 처리 수정 후 사용. API 계약 변경이 프론트엔드에 영향을 줄 수 있을 때 사용.
---

## Purpose

REST API 응답 형식의 일관성, 에러 코드 표준, 프론트엔드 API 클라이언트와의 동기화를 검증합니다.

## Checks

### 1. 에러 응답 형식 일관성

모든 에러 응답은 `ErrorResponse` 형식을 따라야 합니다:

```json
{
  "error": {
    "code": "NOT_FOUND",
    "message": "Inquiry not found: {id}",
    "status": 404,
    "requestId": "uuid",
    "timestamp": "2026-03-08T...",
    "details": null
  }
}
```

- [ ] 모든 Controller가 GlobalExceptionHandler를 통해 에러를 처리하는가?
- [ ] 커스텀 예외가 `ErrorResponse` 형식을 따르는가?
- [ ] 에러 코드가 표준 코드 목록에 있는가?

```bash
grep -rn "ResponseEntity.status\|ResponseEntity.badRequest\|ResponseEntity.notFound" backend/app-api/src/main/java/ --include="*.java" | grep -v "test"
```

### 2. 표준 에러 코드

| 코드 | HTTP | 용도 |
|------|------|------|
| VALIDATION_ERROR | 400 | 입력 검증 실패 |
| NOT_FOUND | 404 | 리소스 없음 |
| UNAUTHORIZED | 401 | 인증 실패 |
| FORBIDDEN | 403 | 권한 부족 |
| CONFLICT | 409 | 상태 충돌 |
| RATE_LIMIT_EXCEEDED | 429 | 요청 제한 초과 |
| PAYLOAD_TOO_LARGE | 413 | 파일 크기 초과 |
| INTERNAL_ERROR | 500 | 서버 내부 오류 |

- [ ] 새로운 에러 코드가 위 목록에 없는 경우 GlobalExceptionHandler에 추가되었는가?

### 3. 응답 DTO 명명 규칙

- [ ] 목록 응답: `{Entity}ListResponse` + 페이지네이션 필드 (content, page, size, totalElements, totalPages)
- [ ] 단건 응답: `{Entity}Response`
- [ ] 생성 요청: `Create{Entity}Request`
- [ ] 수정 요청: `Update{Entity}Request` 또는 `Patch{Entity}Request`
- [ ] 모든 DTO가 `record` 타입인가?

### 4. 프론트엔드 API 클라이언트 동기화

**파일**: `frontend/src/lib/api/client.ts`

- [ ] 백엔드 DTO 필드와 프론트엔드 TypeScript 타입이 일치하는가?
- [ ] 새 엔드포인트가 `client.ts`에 추가되었는가?
- [ ] 삭제된 엔드포인트가 `client.ts`에서 제거되었는가?

```bash
# 백엔드 엔드포인트 목록
grep -rn "@GetMapping\|@PostMapping\|@PutMapping\|@PatchMapping\|@DeleteMapping" backend/app-api/src/main/java/ --include="*.java" | grep -v test

# 프론트엔드 API 호출 목록
grep -n "fetch\|apiClient" frontend/src/lib/api/client.ts
```

### 5. 한국어 라벨 동기화

**파일**: `frontend/src/lib/i18n/labels.ts`

백엔드 enum 값에 대응하는 한국어 라벨이 있는지 확인합니다:

- [ ] Verdict: SUPPORTED, CONDITIONAL, REFUTED
- [ ] InquiryStatus: 모든 상태값
- [ ] AnswerStatus: DRAFT, REVIEWED, APPROVED, SENT
- [ ] DocumentStatus: UPLOADED, INDEXING, INDEXED, FAILED
- [ ] Channel: EMAIL, MESSENGER
- [ ] Tone: FORMAL, BRIEF, TECHNICAL
- [ ] KB Category: MANUAL, PROTOCOL, FAQ, SPEC_SHEET, TROUBLESHOOTING, OTHER

```bash
grep -n "label\|Label" frontend/src/lib/i18n/labels.ts
```

### 6. 날짜/시간 형식 일관성

- [ ] 백엔드: `OffsetDateTime` 사용 (TIMESTAMP WITH TIME ZONE)
- [ ] API 응답: ISO 8601 형식 (`2026-03-08T12:00:00+09:00`)
- [ ] 프론트엔드: `new Date()` 또는 `toLocaleDateString('ko-KR')` 파싱

### 7. 페이지네이션 일관성

목록 API의 페이지네이션 파라미터:
- [ ] `page` (0-based), `size` (기본 20), `sort` (선택)
- [ ] 응답: `content`, `page`, `size`, `totalElements`, `totalPages`
- [ ] 프론트엔드 Pagination 컴포넌트와 일치

### 8. 답변 피드백 엔드포인트 확인

**파일**: `AnswerFeedbackController.java`

피드백 API 엔드포인트가 올바르게 정의되어 있는지 확인합니다:

- [ ] `POST /api/v1/inquiries/{inquiryId}/answers/{answerId}/feedback` — 피드백 생성
- [ ] `GET /api/v1/inquiries/{inquiryId}/answers/{answerId}/feedback` — 피드백 조회
- [ ] 요청/응답 DTO가 `record` 타입인가?
- [ ] GlobalExceptionHandler를 통한 에러 처리가 되는가?
- [ ] 프론트엔드 `client.ts`에 피드백 API 호출이 추가되었는가?

```bash
# 피드백 엔드포인트 확인
grep -n "@PostMapping\|@GetMapping\|feedback\|Feedback" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/feedback/AnswerFeedbackController.java

# 프론트엔드 동기화 확인
grep -n "feedback" frontend/src/lib/api/client.ts
```

## Related Files

| File | Purpose |
|------|---------|
| `backend/app-api/src/main/java/.../common/exception/ErrorResponse.java` | 에러 응답 record |
| `backend/app-api/src/main/java/.../common/exception/GlobalExceptionHandler.java` | 글로벌 예외 처리 |
| `backend/app-api/src/main/java/.../interfaces/rest/` | REST Controller + DTO |
| `frontend/src/lib/api/client.ts` | 프론트엔드 API 클라이언트 |
| `frontend/src/lib/i18n/labels.ts` | 한국어 라벨 매핑 |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/feedback/AnswerFeedbackController.java` | 답변 피드백 엔드포인트 (POST/GET feedback) |
