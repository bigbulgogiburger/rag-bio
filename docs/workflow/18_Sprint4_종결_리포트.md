# 18. Sprint4 종결 리포트

## Sprint 목표
- Retriever → Verifier → Composer 체인 기반 답변 초안 생성
- 채널별 포맷(email/messenger) 및 가드레일 적용
- 승인 플로우(DRAFT/REVIEWED/APPROVED) + 히스토리
- 평가 지표/리포트 체계 확보

## 완료 범위 (DoD)
- [x] S4-01 답변 생성 서비스 + 톤 분기
- [x] S4-02 구조화 응답/출처 포함
- [x] S4-03 출처 UI 구조화 표시(chunk/score)
- [x] S4-04 오케스트레이션 체인화(Retrieve/Verify/Compose)
- [x] S4-05 단계 실행 로그 저장 + 조회 API
- [x] S4-06 실패 폴백(안전 초안) + 단위/통합 테스트
- [x] 품질 리포트(기본/실데이터/holdout/채널분리/format-breakdown)

## 핵심 성과
- 실데이터 기준 답변 품질 지표
  - Accuracy 93.33%
  - Citation 100.00%
  - Guardrail 100.00%
- Holdout 기준 재현성
  - Accuracy 91.67%
  - Citation 100.00%
  - Guardrail 100.00%
- 채널 분리(email/messenger) 기준 지표 안정 확인
- 폴백 경로에서 API 안정성(200 + 안전 응답) 확인

## 남은 리스크 / 기술부채
1. 포맷 실패 분포는 fixture 기반 검증 중심 (실운영 실패 샘플 축적 필요)
2. orchestration-runs 조회는 추가 필터(기간/상태/step) 미지원
3. 발송 단계(SENT) 상태머신 및 권한(RBAC) 미도입

## Sprint5 인계 항목
1. 승인 후 발송 상태(SENT) 확장
2. 발송 어댑터(email/messenger) 인터페이스 및 idempotency
3. 권한/감사로그 강화(RBAC + 조회 UX)
4. 평가 자동화 CI 스케줄(야간/수동 트리거)

## 참고 리포트
- `docs/workflow/reports/sprint4_answer_eval_report_2026-02-13T00-36-13-829Z.md`
- `docs/workflow/reports/sprint4_answer_eval_report_2026-02-13T00-47-06-898Z.md`
- `docs/workflow/reports/sprint4_answer_eval_report_2026-02-13T01-01-39-783Z.md`
- `docs/workflow/reports/sprint4_answer_eval_report_2026-02-13T01-01-39-884Z.md`
- `docs/workflow/reports/sprint4_format_breakdown_report_2026-02-13T01-27-20-705Z.md`
