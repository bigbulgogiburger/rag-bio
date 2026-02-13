# 19. Sprint5 실행백로그

## 목표
- 승인 이후 발송(SENT) 상태머신 확장
- 채널별 발송 준비/검증/중복방지 기반 마련

## Sprint5 티켓

### S5-03-1 상태머신 확장 (P0)
- [x] `APPROVED -> SENT` 전이 추가
- [x] 승인 없이 발송 시 409
- [x] 발송 API 초안: `POST /api/v1/inquiries/{inquiryId}/answers/{answerId}/send`

### S5-03-2 전이 메타데이터 (P0)
- [x] sentBy / sentAt / sendChannel / sendMessageId 저장

### S5-05-1 발송 어댑터 인터페이스 (P0)
- [x] Mock sender(email/messenger) 구현

### S5-05-2 idempotency (P1)
- [x] sendRequestId 기반 중복 발송 차단
- [x] 동일 sendRequestId 재요청 시 동일 sendMessageId 재사용 검증

### S5-04-1 RBAC 최소 적용 (P1)
- [x] approve/send 권한 분리(초기 mock role, `X-Role` 헤더)

### S5-QA-1 통합 테스트 (P0)
- [x] Draft→Review→Approve→Send
- [x] 승인 없는 approve 실패(403, mock RBAC)
