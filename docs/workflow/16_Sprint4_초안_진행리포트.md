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

## 다음 작업
- 승인 플로우(Draft→Review→Approved)와 연결
- 답변 버전 히스토리 조회 API 추가
