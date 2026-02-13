# 16. Sprint4 초안 진행리포트

## 완료 항목 (1차)
- 답변 초안 생성 API 추가
  - `POST /api/v1/inquiries/{inquiryId}/answers/draft`
- Answer Composer 서비스 추가
  - 분석 결과(verdict/confidence/evidence)를 기반으로 CS용 초안 문장 생성
- 프론트 UI 연결
  - 분석 섹션에서 `CS 답변 초안 생성` 실행 가능
  - 생성된 draft/citations/riskFlags 화면 표시
- OpenAPI 반영
  - `AnswerDraftRequest`, `AnswerDraftResponse` 스키마 추가

## 추가 완료 (2차)
- 채널 포맷 분기 추가
  - 입력 옵션: `channel=email|messenger`
  - email: 인사/본문/마무리 포함 템플릿
  - messenger: 요약 중심 짧은 템플릿

## 추가 완료 (3차)
- 품질 가드레일 강화
  - confidence < 0.75 인 경우 `추가 확인 필요` 안내 문구 자동 삽입
  - riskFlags 존재 시 단정적 결론 지양 경고 문구 자동 삽입

## 추가 완료 (4차)
- 승인 플로우 + 버전 히스토리 구현
  - `POST /api/v1/inquiries/{inquiryId}/answers/{answerId}/review`
  - `POST /api/v1/inquiries/{inquiryId}/answers/{answerId}/approve`
  - `GET /api/v1/inquiries/{inquiryId}/answers/latest`
  - `GET /api/v1/inquiries/{inquiryId}/answers/history`
- 답변 초안 생성 시 버전 자동 증가 및 DB 저장

## 추가 완료 (5차)
- 승인자/검토자 메타데이터 추가
  - review/approve 요청에 `actor`, `comment` 전달 가능
  - 응답/히스토리에 `reviewedBy`, `reviewComment`, `approvedBy`, `approveComment` 포함
  - DB 마이그레이션 `V8__answer_draft_reviewer_metadata.sql` 추가

## 추가 완료 (6차)
- 답변 품질 평가 스크립트 추가
  - 파일: `scripts/evaluate_sprint4_answers.mjs`
  - 지표: Verdict Accuracy, Citation Inclusion Rate, Low-Confidence Guardrail Coverage, Risk Guardrail Coverage
  - 리포트 출력: `docs/workflow/reports/sprint4_answer_eval_report_*.md`

## 추가 완료 (7차)
- Sprint4 평가 리포트 1차/재평가 실행
  - baseline(인덱싱 없는 상태): Accuracy 40.00%, Citation 0.00%
  - 실데이터(인덱싱 완료 inquiry): Accuracy 93.33%, Citation 100.00%
  - Guardrail coverage(저신뢰/리스크): 100% 유지

## 추가 완료 (8차)
- holdout answer evalset 추가 및 재현성 체크 완료
  - evalset: `backend/app-api/src/main/resources/evaluation/sprint4_answer_evalset_holdout_v1.json` (12 cases)
  - holdout 재평가 결과: Accuracy 91.67%, Citation 100.00%
  - Guardrail coverage(저신뢰/리스크): 100% 유지

## 추가 완료 (9차)
- 채널별(email/messenger) holdout 분리 리포트 추가
  - email: `docs/workflow/reports/sprint4_answer_eval_report_2026-02-13T01-01-39-783Z.md`
  - messenger: `docs/workflow/reports/sprint4_answer_eval_report_2026-02-13T01-01-39-884Z.md`
  - 두 채널 모두 Accuracy 91.67%, Citation 100.00%, Guardrail 100%

## 추가 완료 (10차)
- 채널별 포맷 품질 지표 자동 점검 추가 (`scripts/evaluate_sprint4_answers.mjs`)
  - email: 인사(`안녕하세요`) + 마무리(`감사합니다`) 포함 여부
  - messenger: `[요약]` 태그 포함 + 길이 260자 이하 여부
  - 신규 메트릭: `Channel Format Pass Rate`

## 추가 완료 (11차)
- 포맷 경고/구조화 출처/평가 가이드 반영
  - 답변 API 응답에 `formatWarnings` 추가
  - FE에서 citation을 `chunk/score` 구조로 표시
  - 포맷 실패 점검용 evalset 추가: `sprint4_answer_evalset_format_fail_v1.json`
  - 평가 실행 가이드 문서 추가: `docs/workflow/17_Sprint4_평가_실행가이드.md`

## 추가 완료 (12차)
- format-fail evalset 실행 리포트 추가
  - report: `docs/workflow/reports/sprint4_answer_eval_report_2026-02-13T01-11-53-478Z.md`
  - Channel Format Failure Breakdown 확인값: `none`

## 추가 완료 (13차)
- S4-05 1차: 단계별 오케스트레이션 실행 로그 저장 추가
  - migration: `V9__orchestration_runs.sql`
  - step(RETRIEVE/VERIFY/COMPOSE), status, latencyMs, errorMessage 저장

## 추가 완료 (14차)
- 오케스트레이션 실행 로그 조회 API 추가
  - `GET /api/v1/inquiries/{inquiryId}/orchestration-runs`
  - step/status/latency/error/createdAt 반환

## 추가 완료 (15차)
- S4-06 폴백 검증 테스트 추가
  - `AnswerComposerServiceFallbackTest`
  - 오케스트레이션 강제 실패 시 CONDITIONAL + `ORCHESTRATION_FALLBACK` + `FALLBACK_DRAFT_USED` 검증

## 추가 완료 (16차)
- S4-06 폴백 경로 통합 테스트(API 레벨) 추가
  - `AnswerFallbackIntegrationTest`
  - 오케스트레이션 강제 실패(MockBean) 시 `/answers/draft`가 200 + 안전 폴백 응답 반환 검증

## 추가 완료 (17차)
- S4-06 폴백 + orchestration-runs FAILED step 가시성 점검 완료
  - `OrchestrationRunVisibilityIntegrationTest`
  - RETRIEVE step 강제 실패 시 `/orchestration-runs`에서 `status=FAILED` 조회 검증

## 다음 작업
- Sprint4 종료 정리(DoD 체크리스트/남은 리스크/다음 스프린트 인계) 문서화
