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

## 다음 작업
- Sprint4 평가 리포트 1차 실행 및 기준선 수치 확정
