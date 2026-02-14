#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   BASE_URL=http://localhost:8080 INQUIRY_ID=<uuid> ./scripts/benchmark_audit_logs.sh
# Optional:
#   RUNS=5

BASE_URL="${BASE_URL:-http://localhost:8080}"
INQUIRY_ID="${INQUIRY_ID:-}"
RUNS="${RUNS:-5}"

if [[ -z "$INQUIRY_ID" ]]; then
  echo "[ERROR] INQUIRY_ID is required"
  echo "example: BASE_URL=http://localhost:8080 INQUIRY_ID=<uuid> $0"
  exit 1
fi

echo "== Audit Logs Latency Benchmark =="
echo "BASE_URL=$BASE_URL"
echo "INQUIRY_ID=$INQUIRY_ID"
echo "RUNS=$RUNS"

tmp_file="$(mktemp)"
trap 'rm -f "$tmp_file"' EXIT

for i in $(seq 1 "$RUNS"); do
  t=$(curl -sS -o /dev/null -w "%{time_total}" \
    "$BASE_URL/api/v1/inquiries/$INQUIRY_ID/answers/audit-logs?page=0&size=20&sort=createdAt,desc")
  echo "$t" >> "$tmp_file"
  printf "run %02d: %ss\n" "$i" "$t"
done

awk '
{ sum += $1; if (NR==1 || $1<min) min=$1; if (NR==1 || $1>max) max=$1; arr[NR]=$1 }
END {
  n=NR
  if (n==0) { print "no data"; exit 1 }
  asort(arr)
  if (n%2==1) p50=arr[(n+1)/2]; else p50=(arr[n/2]+arr[n/2+1])/2
  printf "\nsummary: avg=%.4fs, p50=%.4fs, min=%.4fs, max=%.4fs (n=%d)\n", sum/n, p50, min, max, n
}
' "$tmp_file"
