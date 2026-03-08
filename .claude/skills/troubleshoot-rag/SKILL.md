---
name: troubleshoot-rag
description: RAG 파이프라인 디버깅 가이드 (검색 품질, 할루시네이션, 인용 오류, 답변 품질 등). RAG 문제, 답변 품질 이슈, 검색이 안 됨, 할루시네이션, 인용 오류, 파이프라인 실패 시 사용. 답변 품질에 대한 불만이 있을 때도 자동 트리거.
---

## Purpose

RAG 파이프라인의 각 단계에서 발생할 수 있는 문제를 체계적으로 진단하고 해결합니다.

## Pipeline Overview

```
질문 입력
  ↓
[DECOMPOSE] 질문 분해 (multi-question 탐지)
  ↓
[EXTRACT_PRODUCTS] 제품명 추출 (confidence 0.6-0.9)
  ↓
[RETRIEVE Level 0] 제품 필터 정확 검색
  ↓ (0 결과)
[RETRIEVE Level 1] 카테고리 확장 검색
  ↓ (0 결과)
[RETRIEVE Level 2] 필터 제거 전체 검색
  ↓ (0 결과)
[ADAPTIVE] AI 기반 적응형 검색 (3회 재시도)
  ↓
[MULTI_HOP] 교차 추론 2단계 검색
  ↓
[VERIFY] 근거 검증 (SUPPORTED/CONDITIONAL/REFUTED)
  ↓
[COMPOSE] 답변 작성
  ↓
[CRITIC] 자가 비평 (최대 3회)
  ↓
[SELF_REVIEW] 품질 검토 (최대 2회)
  ↓
답변 초안 완성
```

## Symptom → Diagnosis Map

### 1. 검색 결과 없음 (No Evidence)

**증상**: 답변이 "확인 후 별도로 답변드리겠습니다"로 생성됨

**진단 체크리스트**:
1. 문서가 인덱싱되었는가?
   ```bash
   # API로 인덱싱 상태 확인
   curl localhost:8081/api/v1/inquiries/{id}/documents/indexing-status
   ```
2. 벡터 DB에 청크가 존재하는가?
   - `VECTOR_DB_PROVIDER` 설정 확인 (mock이면 영구 저장 안 됨)
3. 제품명이 올바르게 추출되었는가?
   - `ProductExtractorService`의 12개 Bio-Rad 제품 패턴 확인
   - 로그: `product.extracted` 키워드로 검색
4. 최소 벡터 스코어 임계값 확인
   - `MIN_VECTOR_SCORE=0.25` (application.yml)
   - 너무 높으면 결과 필터링됨

**해결**: 인덱싱 재실행, 제품 패턴 추가, 임계값 조정

### 2. 검색 품질 낮음 (Irrelevant Evidence)

**증상**: 근거는 있지만 질문과 관련 없는 내용

**진단 체크리스트**:
1. HyDE 가상 문서 품질 확인
   - 로그: `hyde.generated` 키워드
2. 리랭킹 점수 분포 확인
   - 0.4 미만이 대부분이면 검색 품질 문제
3. 하이브리드 검색 가중치 확인
   - `vector-weight: 1.0`, `keyword-weight: 1.0` (application.yml)
4. 청크 크기/오버랩 확인
   - 청크가 너무 작으면 문맥 손실, 너무 크면 노이즈
5. Parent-Child 청크 구조 확인
   - CHILD 매칭 시 PARENT 내용이 LLM에 제공되는지

**해결**: 리랭킹 임계값 조정, 청크 전략 변경, HyDE 프롬프트 개선

### 3. 할루시네이션 (Hallucination)

**증상**: 근거에 없는 내용이 답변에 포함됨

**진단 체크리스트**:
1. CriticAgent 결과 확인
   - `faithfulness_score < 0.70`이면 needs_revision=true
   - 로그: `critic.result` 키워드
2. ComposeStep 프롬프트 확인
   - `prompts/compose-system.txt`에 "근거에 없는 내용 금지" 규칙 있는지
3. EVIDENCE_CHAR_BUDGET (12,000자) 초과 여부
   - 근거가 잘리면 LLM이 빈 공간을 추측으로 채울 수 있음
4. 모델 티어 확인
   - 경량 모델일수록 할루시네이션 위험 증가

**해결**: CriticAgent 임계값 조정, 프롬프트 강화, 모델 업그레이드

### 4. 인용 오류 (Citation Errors)

**증상**: 인용 형식 불일치, 잘못된 페이지 번호

**진단 체크리스트**:
1. Citation 형식 확인
   - 프론트엔드 파싱: `chunk=UUID score=... documentId=... fileName=... pageStart=... pageEnd=...`
2. ComposeStep의 인라인 인용 지시 확인
   - `(파일명, p.XX-YY)` 형식 사용하는지
3. 번호 인용([1], [2]) 사용 금지 확인
4. 페이지 범위 정확성
   - ChunkingService의 콘텐츠 기반 페이지 매핑 확인

**해결**: 프롬프트에 인용 형식 규칙 보강, 페이지 매핑 로직 수정

### 5. 톤/포맷 불일치

**증상**: 격식체가 아니거나, 마크다운이 포함되거나, 채널 포맷이 맞지 않음

**진단 체크리스트**:
1. `preferredTone` 파라미터 전달 확인
2. `customerChannel` 파라미터 전달 확인 (EMAIL/MESSENGER)
3. ComposeStep 프롬프트의 톤/채널 규칙 확인
   - 이메일: 인사말 + 본문 + 마무리
   - 메신저: [요약] 태그, 260자 이내

**해결**: 프롬프트 톤 규칙 수정, 채널별 분기 로직 확인

### 6. 파이프라인 실패 (Step Failure)

**증상**: 특정 단계에서 예외 발생

**진단 도구**:
```sql
-- 오케스트레이션 실행 로그 조회
SELECT step, status, latency_ms, error_message, created_at
FROM orchestration_runs
WHERE inquiry_id = '{id}'
ORDER BY created_at DESC;
```

**Fallback 체인**:
| Step | 실패 시 Fallback |
|------|-----------------|
| OpenAiVerifyStep | → DefaultVerifyStep (규칙 기반) |
| OpenAiComposeStep | → DefaultComposeStep (템플릿 기반) |
| CriticAgent | → 원본 draft 사용 |
| MultiHopRetriever | → 기존 evidences 유지 |
| AdaptiveRetrieval | → NO_EVIDENCE 경로 |
| SelfReviewStep | → 원본 draft 반환 (최대 2회 재시도) |

## Key Configuration

| 설정 | 위치 | 기본값 | 영향 |
|------|------|--------|------|
| `OPENAI_ENABLED` | .env | false | true여야 실제 LLM 사용 |
| `MIN_VECTOR_SCORE` | application.yml | 0.25 | 벡터 검색 최소 점수 |
| `HYBRID_SEARCH_ENABLED` | application.yml | true | 하이브리드 검색 활성화 |
| `search.hybrid.rrf-k` | application.yml | 60 | RRF 퓨전 K값 |
| EVIDENCE_CHAR_BUDGET | OpenAiComposeStep | 12,000 | 근거 텍스트 최대 길이 |

## Related Files

| File | Purpose |
|------|---------|
| `backend/app-api/src/main/java/.../answer/orchestration/AnswerOrchestrationService.java` | 파이프라인 오케스트레이터 |
| `backend/app-api/src/main/java/.../search/HybridSearchService.java` | 하이브리드 검색 |
| `backend/app-api/src/main/java/.../analysis/AnalysisService.java` | 증거 분석 |
| `backend/app-api/src/main/resources/prompts/` | 외부화된 프롬프트 |
| `backend/app-api/src/main/resources/application.yml` | 검색/모델 설정 |
