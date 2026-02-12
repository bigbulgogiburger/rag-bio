# 15. Sprint3 1차 진행 리포트

## 완료 항목
- S3-01 질의 검색 API 1차
  - `POST /api/v1/inquiries/{inquiryId}/analysis`
  - 임베딩(mock) + 벡터 검색(mock) + topK 반환
- S3-02 retrieval evidence 저장
  - `retrieval_evidence` 테이블 기록
- S3-03 verdict policy 1차
  - SUPPORTED / REFUTED / CONDITIONAL
  - confidence, reason, riskFlags 계산
- S3-05 결과 UI 1차
  - verdict/confidence/reason/riskFlags/evidence 표시

## 구현 특징
- 비용 없는 mock 기반으로 빠르게 파이프라인 검증
- 구조는 실연동(OpenAI vector/LLM verifier) 교체 가능한 형태 유지

## 추가 완료 (S3-04/06/07 1차)
- 상충근거 리스크 규칙 고도화
  - 점수 편차 기반 + 근거 polarity 충돌 기반 감지
  - 상충 시 verdict를 CONDITIONAL로 보정
- 평가셋 v1 추가
  - `backend/app-api/src/main/resources/evaluation/sprint3_evalset_v1.json`
- 자동 리포트 스크립트 추가
  - `scripts/evaluate_sprint3.mjs`
  - 사용: `INQUIRY_ID=<uuid> node scripts/evaluate_sprint3.mjs`

## 다음 작업
- 평가셋 샘플을 30~50건으로 확장
- verdict 기준(도메인 규칙) 고도화
- 실제 Vector Store/LLM verifier adapter 교체
