# Sprint4 Answer Quality Evaluation Report

- API_BASE: http://localhost:18080
- INQUIRY_ID: f95b4d81-08b4-48a1-bd20-7cc9e2c8774b
- Evalset: backend/app-api/src/main/resources/evaluation/sprint3_evalset_v1.json
- Tone/Channel: professional/email
- Total: 30

## Summary Metrics
- Verdict Accuracy: 28/30 (93.33%)
- Citation Inclusion Rate: 30/30 (100.00%)
- Low-Confidence Guardrail Coverage: 6/6 (100.00%)
- Risk Guardrail Coverage: 4/4 (100.00%)

## Case Results
- ✅ EVAL-001 expected=SUPPORTED, actual=SUPPORTED, conf=0.606, citations=1, status=DRAFT, v=1
- ❌ EVAL-002 expected=REFUTED, actual=CONDITIONAL, conf=1, citations=1, status=DRAFT, v=2
- ✅ EVAL-003 expected=CONDITIONAL, actual=CONDITIONAL, conf=0.816, citations=1, status=DRAFT, v=3
- ✅ EVAL-004 expected=CONDITIONAL, actual=CONDITIONAL, conf=0.642, citations=1, status=DRAFT, v=4
- ✅ EVAL-005 expected=SUPPORTED, actual=SUPPORTED, conf=0.661, citations=1, status=DRAFT, v=5
- ✅ EVAL-006 expected=REFUTED, actual=REFUTED, conf=0.596, citations=1, status=DRAFT, v=6
- ✅ EVAL-007 expected=SUPPORTED, actual=SUPPORTED, conf=0.991, citations=1, status=DRAFT, v=7
- ✅ EVAL-008 expected=REFUTED, actual=REFUTED, conf=0.999, citations=1, status=DRAFT, v=8
- ✅ EVAL-009 expected=CONDITIONAL, actual=CONDITIONAL, conf=0.999, citations=1, status=DRAFT, v=9
- ✅ EVAL-010 expected=CONDITIONAL, actual=CONDITIONAL, conf=0.998, citations=1, status=DRAFT, v=10
- ✅ EVAL-011 expected=SUPPORTED, actual=SUPPORTED, conf=0.999, citations=1, status=DRAFT, v=11
- ✅ EVAL-012 expected=REFUTED, actual=REFUTED, conf=1, citations=1, status=DRAFT, v=12
- ✅ EVAL-013 expected=CONDITIONAL, actual=CONDITIONAL, conf=0.997, citations=1, status=DRAFT, v=13
- ✅ EVAL-014 expected=CONDITIONAL, actual=CONDITIONAL, conf=0.993, citations=1, status=DRAFT, v=14
- ✅ EVAL-015 expected=SUPPORTED, actual=SUPPORTED, conf=0.998, citations=1, status=DRAFT, v=15
- ❌ EVAL-016 expected=REFUTED, actual=CONDITIONAL, conf=0.986, citations=1, status=DRAFT, v=16
- ✅ EVAL-017 expected=CONDITIONAL, actual=CONDITIONAL, conf=1, citations=1, status=DRAFT, v=17
- ✅ EVAL-018 expected=SUPPORTED, actual=SUPPORTED, conf=0.996, citations=1, status=DRAFT, v=18
- ✅ EVAL-019 expected=REFUTED, actual=REFUTED, conf=0.992, citations=1, status=DRAFT, v=19
- ✅ EVAL-020 expected=CONDITIONAL, actual=CONDITIONAL, conf=0.933, citations=1, status=DRAFT, v=20
- ✅ EVAL-021 expected=SUPPORTED, actual=SUPPORTED, conf=0.667, citations=1, status=DRAFT, v=21
- ✅ EVAL-022 expected=REFUTED, actual=REFUTED, conf=0.773, citations=1, status=DRAFT, v=22
- ✅ EVAL-023 expected=CONDITIONAL, actual=CONDITIONAL, conf=0.995, citations=1, status=DRAFT, v=23
- ✅ EVAL-024 expected=SUPPORTED, actual=SUPPORTED, conf=0.999, citations=1, status=DRAFT, v=24
- ✅ EVAL-025 expected=REFUTED, actual=REFUTED, conf=0.999, citations=1, status=DRAFT, v=25
- ✅ EVAL-026 expected=CONDITIONAL, actual=CONDITIONAL, conf=0.999, citations=1, status=DRAFT, v=26
- ✅ EVAL-027 expected=SUPPORTED, actual=SUPPORTED, conf=0.978, citations=1, status=DRAFT, v=27
- ✅ EVAL-028 expected=REFUTED, actual=REFUTED, conf=0.996, citations=1, status=DRAFT, v=28
- ✅ EVAL-029 expected=CONDITIONAL, actual=CONDITIONAL, conf=0.995, citations=1, status=DRAFT, v=29
- ✅ EVAL-030 expected=CONDITIONAL, actual=CONDITIONAL, conf=0.633, citations=1, status=DRAFT, v=30