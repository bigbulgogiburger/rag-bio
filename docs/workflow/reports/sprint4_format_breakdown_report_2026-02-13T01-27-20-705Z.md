# Sprint4 Format Breakdown Validation Report

- Fixture: backend/app-api/src/main/resources/evaluation/sprint4_format_breakdown_fixture_v1.json
- Total: 6
- Matched: 6/6

## Breakdown
- email: greeting missing: 1
- email: closing missing: 1
- email: greeting+closing missing: 1
- messenger: summary tag missing: 1
- messenger: length overflow: 1
- messenger: summary tag missing + length overflow: 1

## Case Results
- ✅ FMT-EMAIL-001 [email] expected=email: greeting missing / actual=email: greeting missing
- ✅ FMT-EMAIL-002 [email] expected=email: closing missing / actual=email: closing missing
- ✅ FMT-EMAIL-003 [email] expected=email: greeting+closing missing / actual=email: greeting+closing missing
- ✅ FMT-MSG-001 [messenger] expected=messenger: summary tag missing / actual=messenger: summary tag missing
- ✅ FMT-MSG-002 [messenger] expected=messenger: length overflow / actual=messenger: length overflow
- ✅ FMT-MSG-003 [messenger] expected=messenger: summary tag missing + length overflow / actual=messenger: summary tag missing + length overflow