#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL=${API_BASE_URL:-http://localhost:18080}
INQUIRY_ID=${INQUIRY_ID:-}

if [[ -z "${INQUIRY_ID}" ]]; then
  echo "INQUIRY_ID is required"
  echo "Example: INQUIRY_ID=<uuid> API_BASE_URL=http://localhost:18080 scripts/run_sprint4_eval_suite.sh"
  exit 1
fi

echo "[1/5] Real-data eval"
API_BASE_URL="$API_BASE_URL" INQUIRY_ID="$INQUIRY_ID" \
  node scripts/evaluate_sprint4_answers.mjs

echo "[2/5] Holdout eval"
API_BASE_URL="$API_BASE_URL" INQUIRY_ID="$INQUIRY_ID" \
  EVALSET_PATH=backend/app-api/src/main/resources/evaluation/sprint4_answer_evalset_holdout_v1.json \
  node scripts/evaluate_sprint4_answers.mjs

echo "[3/5] Holdout email split"
API_BASE_URL="$API_BASE_URL" INQUIRY_ID="$INQUIRY_ID" \
  EVALSET_PATH=backend/app-api/src/main/resources/evaluation/sprint4_answer_evalset_holdout_v1.json \
  ANSWER_CHANNEL=email node scripts/evaluate_sprint4_answers.mjs

echo "[4/5] Holdout messenger split"
API_BASE_URL="$API_BASE_URL" INQUIRY_ID="$INQUIRY_ID" \
  EVALSET_PATH=backend/app-api/src/main/resources/evaluation/sprint4_answer_evalset_holdout_v1.json \
  ANSWER_CHANNEL=messenger node scripts/evaluate_sprint4_answers.mjs

echo "[5/5] Format breakdown fixture validation"
node scripts/evaluate_sprint4_format_breakdown.mjs

echo "Done. Check docs/workflow/reports/"
