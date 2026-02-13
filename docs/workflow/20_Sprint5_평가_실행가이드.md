# 20. Sprint5 평가 실행가이드

## 목적
- Sprint5의 발송 워크플로우 품질을 수치로 확인한다.
- 확인 지표:
  - RBAC 차단율(approve 무권한 403)
  - 승인 전 발송 차단율(409)
  - Send Ready Rate(approve+send 성공률)
  - Duplicate Block Rate(동일 sendRequestId 중복차단)

## 사전 조건
- API 서버 실행 중 (`http://localhost:8080` 기본)
- DB migration 최신 반영(V11 포함)

## 실행
```bash
cd /Users/pyeondohun/pienClaw
API_BASE_URL=http://localhost:8080 \
EVALSET_PATH=backend/app-api/src/main/resources/evaluation/sprint3_evalset_v1.json \
SAMPLE_SIZE=5 \
ANSWER_TONE=professional \
ANSWER_CHANNEL=email \
node scripts/evaluate_sprint5_send_flow.mjs
```

## 산출물
- `docs/workflow/reports/sprint5_send_eval_report_<timestamp>.md`

## 해석 기준(초기)
- RBAC 차단율: 100%
- 승인 전 발송 차단율: 100%
- Send Ready Rate: 100%
- Duplicate Block Rate: 100%

목표 미달 시 원인(권한헤더 누락/상태머신 전이 오류/idempotency 로직 누락)을 케이스 결과에서 확인한다.
