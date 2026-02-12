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

## 다음 작업
- 답변 템플릿 다변화(정중/기술/요약 톤)
- 이메일/메신저 채널 포맷 분기
- 승인 플로우(Draft→Review→Approved)와 연결
