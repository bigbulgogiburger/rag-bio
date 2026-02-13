# 25. Sprint6 감사로그 조회 예시

## 엔드포인트
`GET /api/v1/inquiries/{inquiryId}/answers/audit-logs`

## 파라미터
- `status`: DRAFT | REVIEWED | APPROVED | SENT
- `actor`: reviewedBy/approvedBy/sentBy 중 일치 사용자
- `from`, `to`: ISO-8601 UTC 시간
- `page`, `size`: 페이지네이션
- `sort`: 예) `createdAt,desc`

## 예시
### 1) 최근 승인 로그 20건
`/api/v1/inquiries/{inquiryId}/answers/audit-logs?status=APPROVED&page=0&size=20&sort=createdAt,desc`

### 2) 특정 사용자(approver-1) 활동 로그
`/api/v1/inquiries/{inquiryId}/answers/audit-logs?actor=approver-1&sort=createdAt,desc`

### 3) 기간 필터(하루)
`/api/v1/inquiries/{inquiryId}/answers/audit-logs?from=2026-02-13T00:00:00Z&to=2026-02-13T23:59:59Z&sort=createdAt,asc`
