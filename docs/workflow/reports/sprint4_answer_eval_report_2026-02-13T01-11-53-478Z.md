# Sprint4 Answer Quality Evaluation Report

- API_BASE: http://localhost:18080
- INQUIRY_ID: 3e94d69a-04f2-4b3b-90c1-1c4804e000a6
- Evalset: backend/app-api/src/main/resources/evaluation/sprint4_answer_evalset_format_fail_v1.json
- Tone/Channel: professional/messenger
- Total: 5

## Summary Metrics
- Verdict Accuracy: 4/5 (80.00%)
- Citation Inclusion Rate: 5/5 (100.00%)
- Low-Confidence Guardrail Coverage: 1/1 (100.00%)
- Risk Guardrail Coverage: 0/0 (0.00%)
- Channel Format Pass Rate: 5/5 (100.00%)

## Channel Format Failure Breakdown
- none

## Case Results
- ✅ S4F-001 expected=SUPPORTED, actual=SUPPORTED, conf=0.77, citations=1, format=OK(ok), status=DRAFT, v=1
- ✅ S4F-002 expected=REFUTED, actual=REFUTED, conf=0.979, citations=1, format=OK(ok), status=DRAFT, v=2
- ❌ S4F-003 expected=CONDITIONAL, actual=SUPPORTED, conf=0.989, citations=1, format=OK(ok), status=DRAFT, v=3
- ✅ S4F-004 expected=CONDITIONAL, actual=CONDITIONAL, conf=0.434, citations=1, format=OK(ok), status=DRAFT, v=4
- ✅ S4F-005 expected=SUPPORTED, actual=SUPPORTED, conf=0.943, citations=1, format=OK(ok), status=DRAFT, v=5