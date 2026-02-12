# 04. 에픽 C — RAG/검증/답변 (P0)

## 목표
질문에 대해 근거 기반 verdict와 실행 가능한 답변을 생성한다.

## Story C-1: Retrieval 서비스
### FE
- 검색 근거 리스트 UI(문서/페이지/chunk)
### BE
- query normalize/rewrite
- vector search(top-k) + threshold
- rerank 옵션
### DB
- retrieval_evidence 저장
### AC
- 검색 근거가 요청별로 조회 가능

## Story C-2: 기술 검증(맞/틀/조건부)
### FE
- verdict 배지 + 신뢰도 표시
### BE
- 판정 규칙 엔진
- 상충근거 탐지 + 리스크 플래그
### DB
- verification_result 저장
### AC
- verdict + 근거요약 + 리스크 포함

## Story C-3: 답변 생성
### FE
- 최종 답변 + 추가 확인항목 UI
### BE
- 근거 인용 강제 프롬프트
- structured output schema
### DB
- composed_answer 저장
### AC
- 출처 없는 단정 문장 최소화

## Story C-4: 멀티 에이전트 오케스트레이션
### FE
- 단계별 진행상태 타임라인
### BE
- Retriever→Verifier→Composer 체인
- 실패 단계 폴백
### DB
- agent_run/agent_run_step 기록
### AC
- 단일 요청으로 end-to-end 실행

## 품질 측정
- 정확도, 출처포함률, 재작성률
- 오류 사례 주간 리뷰
