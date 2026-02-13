# 21. Sprint5 종결 리포트

## 기간/범위
- 범위: 승인 이후 커뮤니케이션 단계 안정화
- 핵심 목표:
  - `APPROVED -> SENT` 상태머신 확장
  - 발송 idempotency 보장(`sendRequestId`)
  - approve/send 최소 권한 분리(mock RBAC)
  - 운영 조회성(감사로그 API)
  - 품질지표 자동 평가

## 완료 항목

### 1) 상태머신 및 발송 API
- `APPROVED -> SENT` 전이 구현
- `POST /api/v1/inquiries/{inquiryId}/answers/{answerId}/send` 구현
- sent 메타데이터 저장:
  - `sentBy`, `sentAt`, `sendChannel`, `sendMessageId`

### 2) 발송 어댑터 추상화
- `MessageSender` 인터페이스 도입
- Mock sender 구현:
  - `MockEmailSender`
  - `MockMessengerSender`

### 3) idempotency
- `sendRequestId` 도입 및 중복 발송 차단
- 동일 `sendRequestId` 재요청 시 기존 발송 결과 재사용
- DB migration:
  - `V11__answer_draft_send_request_id.sql`

### 4) 최소 RBAC(mock)
- `X-Role` 헤더 기반 권한 분리
  - approve: `APPROVER`
  - send: `SENDER`
- 권한 미충족 시 403 반환

### 5) 운영 가시성
- 감사로그 조회 API 추가:
  - `GET /api/v1/inquiries/{inquiryId}/answers/audit-logs`
- 조회 필드:
  - 상태/버전, review/approve/send 메타데이터, 생성/수정시각

### 6) 품질 검증/평가
- 통합 테스트 추가:
  - Draft → Review → Approve → Send
  - approve 무권한 403
  - 동일 `sendRequestId` idempotency 검증
- 평가 스크립트/가이드 추가:
  - `scripts/evaluate_sprint5_send_flow.mjs`
  - `docs/workflow/20_Sprint5_평가_실행가이드.md`
- 실제 평가 결과(샘플 5건):
  - RBAC Block Rate: 100%
  - Pre-Approval Send Block Rate: 100%
  - Send Ready Rate: 100%
  - Duplicate Block Rate: 100%
  - 보고서: `docs/workflow/reports/sprint5_send_eval_report_2026-02-13T03-35-24-799Z.md`

## 주요 커밋
- `89cfe4d` feat(s5-03): add APPROVED->SENT transition and send endpoint
- `eeb19c8` feat(s5-05): add message sender interface with mock email/messenger adapters
- `4f83093` feat(s5-05): add sendRequestId idempotency for send endpoint
- `c6358a1` feat(s5-04): enforce mock role headers for approve/send endpoints
- `f85ab60` test(s5-qa): add workflow integration test for role gating and send idempotency
- `a3523fc` feat(s5-06): add answer audit logs endpoint by inquiry
- `02463eb` feat(s5-qa): add sprint5 send workflow evaluation script and guide
- `17e25d4` docs(s5-qa): add sprint5 send workflow evaluation report

## 산출물 요약
- 기능: 발송 단계 핵심 제어(상태/권한/중복방지) 구현 완료
- 문서: Sprint5 실행백로그 + 평가가이드 + 종결리포트 최신화
- 품질: 통합 테스트/평가 리포트로 정량 검증 완료

## 리스크/후속 제안 (Sprint6 후보)
- 실제 외부 채널(sender) 연동 전환 및 장애 재시도 정책
- RBAC를 헤더 mock에서 계정/권한모델 기반으로 고도화
- 감사로그 필터/페이지네이션/기간 검색
- 운영 알림(발송 실패율 급증, 중복요청 급증) 자동화
