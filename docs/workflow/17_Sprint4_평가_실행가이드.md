# 17. Sprint4 평가 실행 가이드

## 1) Baseline (인덱싱 없는 상태)
```bash
INQUIRY_ID=<uuid> API_BASE_URL=http://localhost:18080 \
node scripts/evaluate_sprint4_answers.mjs
```

## 2) Real-data (인덱싱 완료 inquiry)
```bash
INQUIRY_ID=<indexed_uuid> API_BASE_URL=http://localhost:18080 \
node scripts/evaluate_sprint4_answers.mjs
```

## 3) Holdout (기본)
```bash
INQUIRY_ID=<indexed_uuid> API_BASE_URL=http://localhost:18080 \
EVALSET_PATH=backend/app-api/src/main/resources/evaluation/sprint4_answer_evalset_holdout_v1.json \
node scripts/evaluate_sprint4_answers.mjs
```

## 4) Holdout 채널 분리
### email
```bash
INQUIRY_ID=<indexed_uuid> API_BASE_URL=http://localhost:18080 \
EVALSET_PATH=backend/app-api/src/main/resources/evaluation/sprint4_answer_evalset_holdout_v1.json \
ANSWER_CHANNEL=email node scripts/evaluate_sprint4_answers.mjs
```

### messenger
```bash
INQUIRY_ID=<indexed_uuid> API_BASE_URL=http://localhost:18080 \
EVALSET_PATH=backend/app-api/src/main/resources/evaluation/sprint4_answer_evalset_holdout_v1.json \
ANSWER_CHANNEL=messenger node scripts/evaluate_sprint4_answers.mjs
```

## 5) Format-fail 점검 세트
```bash
INQUIRY_ID=<indexed_uuid> API_BASE_URL=http://localhost:18080 \
EVALSET_PATH=backend/app-api/src/main/resources/evaluation/sprint4_answer_evalset_format_fail_v1.json \
ANSWER_CHANNEL=messenger node scripts/evaluate_sprint4_answers.mjs
```

## 리포트 위치
- `docs/workflow/reports/sprint4_answer_eval_report_*.md`
