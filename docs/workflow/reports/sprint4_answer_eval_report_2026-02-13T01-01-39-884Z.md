# Sprint4 Answer Quality Evaluation Report

- API_BASE: http://localhost:18080
- INQUIRY_ID: 873470e6-f061-4559-aca2-ba46b84fe1c3
- Evalset: backend/app-api/src/main/resources/evaluation/sprint4_answer_evalset_holdout_v1.json
- Tone/Channel: professional/messenger
- Total: 12

## Summary Metrics
- Verdict Accuracy: 11/12 (91.67%)
- Citation Inclusion Rate: 12/12 (100.00%)
- Low-Confidence Guardrail Coverage: 9/9 (100.00%)
- Risk Guardrail Coverage: 1/1 (100.00%)

## Case Results
- ✅ S4H-001 expected=SUPPORTED, actual=SUPPORTED, conf=1, citations=1, status=DRAFT, v=13
- ✅ S4H-002 expected=REFUTED, actual=REFUTED, conf=0.643, citations=1, status=DRAFT, v=14
- ✅ S4H-003 expected=CONDITIONAL, actual=CONDITIONAL, conf=0.66, citations=1, status=DRAFT, v=15
- ✅ S4H-004 expected=CONDITIONAL, actual=CONDITIONAL, conf=0.628, citations=1, status=DRAFT, v=16
- ✅ S4H-005 expected=SUPPORTED, actual=SUPPORTED, conf=0.663, citations=1, status=DRAFT, v=17
- ✅ S4H-006 expected=REFUTED, actual=REFUTED, conf=0.625, citations=1, status=DRAFT, v=18
- ✅ S4H-007 expected=CONDITIONAL, actual=CONDITIONAL, conf=0.676, citations=1, status=DRAFT, v=19
- ✅ S4H-008 expected=SUPPORTED, actual=SUPPORTED, conf=0.754, citations=1, status=DRAFT, v=20
- ✅ S4H-009 expected=CONDITIONAL, actual=CONDITIONAL, conf=0.556, citations=1, status=DRAFT, v=21
- ❌ S4H-010 expected=REFUTED, actual=CONDITIONAL, conf=0.755, citations=1, status=DRAFT, v=22
- ✅ S4H-011 expected=CONDITIONAL, actual=CONDITIONAL, conf=0.528, citations=1, status=DRAFT, v=23
- ✅ S4H-012 expected=SUPPORTED, actual=SUPPORTED, conf=0.646, citations=1, status=DRAFT, v=24