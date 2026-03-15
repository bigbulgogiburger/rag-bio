# RAG Pipeline Core 강화 기획서

> **작성 기준**: 2026-03-15 현재 코드베이스 심층 분석 기반
> **대상 브랜치**: `main` (커밋 `b1e04d1`)
> **목표**: 비용 최적화 + 코어 안정성 + 프로덕션 운영 가시성 확보

---

## 1. 현황 진단

### 1.1 현재 파이프라인 흐름 (8단계)

```
DECOMPOSE → RETRIEVE(3-level) → ADAPTIVE_RETRIEVE → MULTI_HOP
    → VERIFY → COMPOSE → CRITIC → SELF_REVIEW
```

### 1.2 문의 1건당 LLM 호출 현황

| 단계 | 모델 티어 | 호출 수 | 토큰/호출 | 합계 토큰 |
|------|----------|---------|-----------|----------|
| Question Decompose | Light (nano) | 1 | ~800 | 800 |
| HyDE 변환 | Light (nano) | 1 | ~1,200 | 1,200 |
| Adaptive Retrieve (평균 1.5회) | Light (nano) | 1.5 | ~600 | 900 |
| Reranking (listwise) | Medium (mini) | 1 | ~2,200 | 2,200 |
| Multi-hop 평가 (30% 트리거) | Medium (mini) | 0.3 | ~1,000 | 300 |
| Verify | Medium (mini) | 1 | ~2,000 | 2,000 |
| Compose (기본) | Heavy (mini) | 1 | ~5,500 | 5,500 |
| Critic (조건부) | Heavy (mini) | 0~1 | ~5,000 | 0~5,000 |
| Recompose (SelfReview 실패시) | Heavy (mini) | 0~2 | ~5,500 | 0~11,000 |
| **일반 케이스 합계** | | | | **~12,900** |
| **최악 케이스 합계** | | | | **~29,200** |

### 1.3 핵심 문제점 5가지

| # | 문제 | 심각도 | 영향 |
|---|------|--------|------|
| **P1** | **토큰 예산 제어 부재** — 문의 1건에 최대 29K 토큰 소비 가능, 상한선 없음 | CRITICAL | Critic→Recompose 루프가 2회 반복하면 Heavy 모델 11K 토큰 추가 |
| **P2** | **토큰 소비 추적 불가** — 단계별 토큰 사용량 로깅/모니터링 없음 | CRITICAL | 비용 이상 감지 불가, 프롬프트 드리프트 시 비용 급증 사전 차단 불가 |
| **P3** | **Vector DB 장애 시 전체 검색 실패** — Qdrant 다운 시 fallback 없음 | HIGH | 검색 단계 실패 → 파이프라인 전체 실패 |
| **P4** | **증거 예산이 문자 기반** — 12,000자 character budget (토큰 아님), 증거 중복/저품질 필터링 없음 | HIGH | 불필요한 중복 증거로 Heavy 모델 토큰 낭비 |
| **P5** | **하이퍼파라미터 하드코딩** — RRF k=60, 청크 크기 1500/400, 신뢰도 임계값 0.85 등 코드에 고정 | MEDIUM | A/B 테스트나 문서 유형별 튜닝 불가 |

---

## 2. 개선 전략 (3 Phase)

### Phase 1: 비용 제어 & 가시성 (P0 — 즉시)

> **목표**: 토큰 예산 강제, 소비 추적, 비용 이상 감지

#### TASK 1-1: 토큰 예산 관리자 (TokenBudgetManager)

**현재**: 각 단계가 독립적으로 LLM 호출, 전체 예산 개념 없음

**개선**:
```
TokenBudgetManager
├── maxBudgetPerInquiry: 25,000 tokens (설정 가능)
├── consumed: AtomicInteger
├── tryConsume(stepName, estimatedTokens) → boolean
├── forceConsume(stepName, actualTokens)  // 실제 사용량 기록
├── getRemainingBudget() → int
└── getConsumptionReport() → Map<String, Integer>
```

**핵심 로직**:
- 각 단계 진입 전 `tryConsume()` 호출 → 예산 초과 시 해당 단계 스킵
- 스킵 우선순위: Critic → SelfReview → Adaptive Retrieve → Multi-hop → Reranking
- 필수 단계(Decompose, Retrieve, Verify, Compose)는 예산과 무관하게 실행
- `application.yml`에서 `rag.budget.max-tokens-per-inquiry: 25000` 설정

**비용 절감 효과**: 최악 케이스 29K → 25K 상한 (14% 절감)

#### TASK 1-2: 단계별 토큰 사용량 추적 (PipelineTokenTracker)

**현재**: `PipelineTraceContext`에 실행 시간만 기록, 토큰 정보 없음

**개선**:
- OpenAI 응답의 `usage.prompt_tokens`, `usage.completion_tokens` 파싱
- `OpenAiRequestUtils`에서 모든 LLM 호출 후 토큰 사용량 자동 추출
- `PipelineTraceContext`에 `tokenUsage` 필드 추가:
  ```
  Map<String, TokenUsage> stepTokenUsage;
  // key: "DECOMPOSE", "VERIFY", "COMPOSE" 등
  // value: { promptTokens, completionTokens, totalTokens, modelId, estimatedCostUsd }
  ```
- `RagPipelineMetricEntity`에 `total_tokens`, `total_estimated_cost_usd` 컬럼 추가
- 답변 초안 응답에 `tokenUsageSummary` 포함 (디버그/운영용)

**운영 가치**: 비용 트렌드 모니터링, 프롬프트 변경 전후 비용 비교 가능

#### TASK 1-3: 비용 알림 및 일일 한도

**현재**: 비용 제한 메커니즘 없음

**개선**:
- `application.yml`:
  ```yaml
  rag:
    cost:
      daily-budget-usd: 50.0        # 일일 예산
      alert-threshold-percent: 80   # 80% 소진 시 경고 로그
      per-inquiry-max-usd: 0.10     # 문의 1건 최대 비용
  ```
- `RagCostGuardService`:
  - 일일 누적 비용 조회 (DB 기반)
  - 임계값 초과 시 WARN 로그 + (향후) 웹훅 알림
  - 일일 한도 초과 시 Compose를 Light 모델로 다운그레이드 (품질 저하 대신 서비스 유지)

---

### Phase 2: 코어 견고성 강화 (P1)

> **목표**: 장애 내성, 증거 품질 향상, 할루시네이션 감소

#### TASK 2-1: 증거 중복 제거 & 토큰 기반 예산

**현재**: 12,000자 character budget, 중복 증거 필터링 없음

**개선**:
- **증거 중복 제거**: 같은 내용의 CHILD/PARENT 쌍에서 PARENT만 전달
  ```
  deduplicateEvidence(List<EvidenceItem> items):
    1. chunkId 기준 중복 제거
    2. 내용 유사도(Jaccard > 0.7) 기준 중복 제거 — 높은 score 쪽 보존
    3. 동일 문서+동일 페이지 범위 → 하나만 유지
  ```
- **토큰 기반 예산**: 12,000자 → `rag.compose.evidence-token-budget: 3000` (토큰 단위)
  - `tiktoken` 라이브러리 또는 간이 토큰 추정기 (영문 4자/토큰, 한글 2자/토큰)
  - 예산 초과 시 score 낮은 증거부터 제거

**비용 절감 효과**: Compose 입력 20~30% 감소 (중복 증거 제거)

#### TASK 2-2: Vector DB Circuit Breaker

**현재**: Qdrant 장애 시 검색 단계 전체 실패 → 파이프라인 중단

**개선**:
```
VectorStoreCircuitBreaker
├── state: CLOSED | OPEN | HALF_OPEN
├── failureThreshold: 3 (연속 실패 3회 → OPEN)
├── resetTimeout: 30초 (OPEN → HALF_OPEN)
├── fallbackStrategy: KEYWORD_ONLY
└── healthCheck(): boolean
```

**장애 시 동작**:
1. Circuit OPEN → Vector 검색 스킵, Keyword 검색만 사용
2. 30초 후 HALF_OPEN → 다음 요청 1건 Vector 시도
3. 성공 → CLOSED, 실패 → OPEN 유지
4. 응답에 `degradedMode: true` 표시 → 프론트엔드에서 "검색 품질 제한" 안내

**추가**: Embedding API 호출에도 지수 백오프 재시도 (최대 3회, 1s → 2s → 4s)

#### TASK 2-3: 증거 품질 게이트 (Evidence Quality Gate)

**현재**: Retrieve 결과를 점수 필터링 없이 Verify/Compose에 전달

**개선**:
- Retrieve 후 **증거 품질 게이트** 삽입:
  ```
  RETRIEVE → [Quality Gate] → VERIFY → COMPOSE
  ```
- 게이트 규칙:
  1. `minScore` 이하 증거 제거 (기본 0.30)
  2. 최대 증거 수 제한 (기본 8개 — 현재 무제한)
  3. 다양성 보장: 동일 문서에서 최대 3개 증거
  4. sourceType 밸런스: KB 증거와 Inquiry 증거 비율 조정 (최소 각 1개 보장)
- `application.yml`에서 설정 가능:
  ```yaml
  rag:
    evidence:
      max-items: 8
      min-score: 0.30
      max-per-document: 3
      ensure-source-diversity: true
  ```

**비용 절감 효과**: 증거 수 제한으로 Verify/Compose 입력 토큰 30~40% 감소

#### TASK 2-4: Compose 프롬프트 효율화

**현재**: 각 증거를 `[근거 N] 파일: ... p.XX\n내용(최대 500자)\n\n` 형식으로 포맷 — 메타데이터 오버헤드 큼

**개선**:
- 증거 포맷 압축:
  ```
  Before: [근거 1] 파일: CFX96_Manual_v3.2.pdf p.45-47\n소스: KNOWLEDGE_BASE\n관련도: 0.92\n내용: ...500자...\n\n
  After:  [1|CFX96_Manual_v3.2.pdf:45-47|KB|0.92] ...500자...
  ```
- 메타데이터 라인 → 인라인 헤더 (1줄): 토큰 40~60% 절감 (메타데이터 부분만)
- 전체 Compose 프롬프트에서 약 200~400 토큰 절감

#### TASK 2-5: Critic 효율화 — 조건부 트리거 강화

**현재**: `topScore >= 0.85 && confidence >= 0.80` 이면 스킵

**개선**:
- 스킵 조건 완화: `topScore >= 0.80 && confidence >= 0.75` → 더 많은 케이스에서 스킵
- **증거 수 기반 스킵**: 증거 5개 이상 + 평균 score >= 0.75 → 스킵 (증거가 풍부하면 Critic 불필요)
- **문의 유형 기반 스킵**: 단순 문의(하위 질문 1개) → Critic 스킵
- Critic 호출 비율 목표: 현재 ~60% → 30%로 감소
- **비용 절감**: Critic 1회당 ~5,000 토큰 × 호출률 30% 감소 = 문의당 평균 1,500 토큰 절감

---

### Phase 3: 운영 고도화 & 피드백 루프 (P2)

> **목표**: 프로덕션 운영 최적화, 지속적 품질 개선 기반 구축

#### TASK 3-1: Semantic Cache (의미론적 캐시)

**현재**: 임베딩만 LRU 캐시 (CachingEmbeddingDecorator), LLM 응답은 매번 재생성

**개선**:
- **Query-Response 캐시**: 유사한 질문에 대해 기존 답변 재사용
  ```
  SemanticCacheService
  ├── put(queryEmbedding, response, metadata)
  ├── get(queryEmbedding, similarityThreshold=0.95) → CachedResponse?
  └── invalidate(documentId)  // 문서 재인덱싱 시 관련 캐시 무효화
  ```
- 캐시 히트 시 전체 파이프라인 스킵 → **비용 100% 절감** (해당 문의)
- 주의: 캐시 키는 질문 임베딩의 코사인 유사도 기반 (threshold 0.95로 높게 설정)
- 캐시 무효화: KB 문서 재인덱싱 시 관련 캐시 자동 삭제

**예상 히트율**: 반복 문의 20~30% → 전체 비용 20~30% 절감 가능

#### TASK 3-2: 하이퍼파라미터 외부화

**현재**: RRF k=60, 청크 크기 1500/400, 신뢰도 임계값 등 코드에 하드코딩

**개선**:
- `application.yml`로 모든 핵심 파라미터 외부화:
  ```yaml
  rag:
    chunking:
      parent-size: 1500
      child-size: 400
      overlap: 300
    search:
      rrf-k: 60
      vector-weight: 1.0
      keyword-weight: 1.0
      min-vector-score: 0.25
      top-k: 5
    confidence:
      high-confidence-score: 0.80
      high-confidence-threshold: 0.75
      supported-threshold: 0.70
      conditional-threshold: 0.45
    adaptive:
      min-confidence: 0.50
      max-retries: 3
    multihop:
      trigger-top-score: 0.70
      trigger-min-evidence: 3
      max-hops: 2
  ```
- 재시작 없이 변경하려면 `@RefreshScope` + Spring Cloud Config 연동 (향후)

#### TASK 3-3: 답변 품질 피드백 API

**현재**: 사용자 피드백 수집 메커니즘 없음

**개선**:
- `POST /api/v1/inquiries/{id}/answers/{answerId}/feedback`:
  ```json
  {
    "rating": "HELPFUL" | "NOT_HELPFUL" | "PARTIALLY_HELPFUL",
    "issues": ["HALLUCINATION", "WRONG_PRODUCT", "INCOMPLETE", "CITATION_ERROR"],
    "comment": "optional free text"
  }
  ```
- 피드백 저장: `answer_feedback` 테이블 (Flyway 마이그레이션)
- 운영 대시보드에 피드백 통계 표시 (만족도 비율, 주요 이슈 유형)
- 향후: 피드백 데이터 기반 하이퍼파라미터 자동 튜닝

#### TASK 3-4: Adaptive Retrieve 효율화

**현재**: 3회 재시도 시 Light 모델 3회 호출 (쿼리 재구성) + Hybrid Search 3회 추가

**개선**:
- **1차 시도에서 복합 전략**: expand + broaden + translate를 1회 LLM 호출로 통합
  ```
  기존: 3개 프롬프트 × 1회씩 = 3 LLM 호출 + 3 Hybrid Search
  개선: 1개 통합 프롬프트 → JSON 배열로 3개 변형 쿼리 생성 = 1 LLM 호출
  ```
- 3개 변형 쿼리를 **병렬** Hybrid Search → 결과 합산 후 중복 제거
- **비용 절감**: LLM 호출 3회 → 1회 (Light 모델 ~1,200 토큰 절감)
- **지연 시간**: 순차 3회 → 병렬 1회 + 병렬 검색 (50~70% 지연 감소)

#### TASK 3-5: 인덱싱 비용 최적화

**현재**: 모든 PARENT 청크에 Contextual Enrichment 적용 (Light 모델 ~2,200 토큰/청크)

**개선**:
- **선택적 Enrichment**: 문서 크기 기반
  - 5 PARENT 미만 → Enrichment 스킵 (전체 문서가 이미 충분히 짧음)
  - 5~30 PARENT → 전체 Enrichment
  - 30 PARENT 초과 → 샘플링 (매 3번째 PARENT만 Enrichment)
- **비용 절감 예시**:
  - 100페이지 매뉴얼 (50 PARENT): 50 × 2,200 = 110K 토큰 → 17 × 2,200 = 37K 토큰 (66% 절감)
  - 5페이지 FAQ (3 PARENT): 3 × 2,200 = 6.6K 토큰 → 0 (스킵, 100% 절감)

---

## 3. 구현 우선순위 & 일정

| 순서 | 태스크 | Phase | 난이도 | 비용 절감 | 안정성 기여 |
|------|--------|-------|--------|----------|------------|
| 1 | TASK 1-1: TokenBudgetManager | P1 | Medium | HIGH | HIGH |
| 2 | TASK 1-2: 토큰 사용량 추적 | P1 | Medium | — (가시성) | HIGH |
| 3 | TASK 2-3: 증거 품질 게이트 | P1 | Low | HIGH | MEDIUM |
| 4 | TASK 2-1: 증거 중복 제거 | P1 | Medium | MEDIUM | MEDIUM |
| 5 | TASK 2-5: Critic 효율화 | P1 | Low | MEDIUM | LOW |
| 6 | TASK 2-4: Compose 프롬프트 효율화 | P1 | Low | LOW | LOW |
| 7 | TASK 2-2: Vector DB Circuit Breaker | P1 | Medium | — | CRITICAL |
| 8 | TASK 1-3: 비용 알림/한도 | P1 | Low | — (운영) | MEDIUM |
| 9 | TASK 3-2: 하이퍼파라미터 외부화 | P2 | Medium | — (유연성) | MEDIUM |
| 10 | TASK 3-4: Adaptive Retrieve 효율화 | P2 | Medium | MEDIUM | LOW |
| 11 | TASK 3-5: 인덱싱 비용 최적화 | P2 | Low | HIGH | LOW |
| 12 | TASK 3-1: Semantic Cache | P2 | High | VERY HIGH | LOW |
| 13 | TASK 3-3: 답변 품질 피드백 API | P2 | Medium | — (품질) | MEDIUM |

---

## 4. 예상 비용 절감 효과

### 문의 1건당 토큰 비교

| 시나리오 | 현재 (토큰) | 개선 후 (토큰) | 절감율 |
|---------|------------|---------------|--------|
| 일반 케이스 | ~12,900 | ~8,500 | **34%** |
| Critic 트리거 케이스 | ~18,000 | ~12,000 | **33%** |
| 최악 케이스 | ~29,200 | ~25,000 (상한) | **14%** |
| 캐시 히트 케이스 | ~12,900 | ~0 | **100%** |

### 절감 세부 내역

| 최적화 항목 | 절감 토큰 | 비고 |
|------------|----------|------|
| 증거 품질 게이트 (max 8개) | ~1,500 | Verify/Compose 입력 감소 |
| 증거 중복 제거 | ~800 | 중복 증거 제거 |
| Compose 포맷 압축 | ~300 | 메타데이터 오버헤드 감소 |
| Critic 호출률 감소 (60%→30%) | ~1,500 | 평균 기준 |
| Adaptive 통합 호출 | ~400 | 3회→1회 |
| **합계** | **~4,500** | 일반 케이스 기준 34% 절감 |

### 인덱싱 비용 (문서 1건, 50 PARENT 기준)

| 항목 | 현재 | 개선 후 | 절감 |
|------|------|---------|------|
| Contextual Enrichment | 110K 토큰 | 37K 토큰 | 66% |
| Embedding | 25K 토큰 | 25K 토큰 | 0% (변동 없음) |

---

## 5. 기술 상세: 주요 변경 파일

### Phase 1 변경 파일

```
신규:
  rag/budget/TokenBudgetManager.java          — 토큰 예산 관리
  rag/budget/TokenUsage.java                  — 토큰 사용량 VO
  rag/cost/RagCostGuardService.java           — 비용 알림/한도
  V35__add_token_usage_columns.sql            — token 추적 컬럼

수정:
  openai/OpenAiRequestUtils.java              — 응답에서 usage 파싱
  openai/PipelineTraceContext.java            — tokenUsage 필드 추가
  answer/orchestration/AnswerOrchestrationService.java — 예산 관리자 연동
  persistence/metrics/RagPipelineMetricEntity.java     — token/cost 컬럼
  application.yml                              — budget/cost 설정
```

### Phase 2 변경 파일

```
신규:
  search/EvidenceQualityGate.java             — 증거 품질 게이트
  search/EvidenceDeduplicator.java            — 증거 중복 제거
  vector/VectorStoreCircuitBreaker.java       — Circuit Breaker

수정:
  analysis/AnalysisService.java               — 품질 게이트 적용
  answer/orchestration/OpenAiComposeStep.java — 포맷 압축, 토큰 기반 예산
  answer/orchestration/AnswerOrchestrationService.java — Critic 조건 강화
  vector/QdrantVectorStore.java               — Circuit Breaker 래핑
  search/HybridSearchService.java             — keyword-only fallback
```

### Phase 3 변경 파일

```
신규:
  cache/SemanticCacheService.java             — 의미론적 캐시
  V36__answer_feedback.sql                    — 피드백 테이블
  feedback/AnswerFeedbackController.java      — 피드백 API
  feedback/AnswerFeedbackEntity.java          — 피드백 엔티티

수정:
  application.yml                              — 하이퍼파라미터 전면 외부화
  search/OpenAiAdaptiveRetrievalAgent.java    — 통합 쿼리 재구성
  chunk/OpenAiContextualChunkEnricher.java    — 선택적 Enrichment
  vector/VectorizingService.java              — Enrichment 정책 적용
```

---

## 6. 품질 검증 전략

### 각 Phase 완료 후 검증

| 검증 항목 | 방법 |
|----------|------|
| 토큰 예산 준수 | golden-dataset 10건 실행, 모든 결과 25K 이하 확인 |
| 비용 추적 정확성 | OpenAI usage 필드 vs 내부 추적 값 일치 검증 |
| 증거 품질 게이트 | 기존 답변 품질 A/B 비교 (게이트 적용 전후) |
| Circuit Breaker | Qdrant 연결 차단 상태에서 keyword-only 검색 동작 확인 |
| Semantic Cache | 동일/유사 질문 반복 시 캐시 히트 확인, 답변 일관성 검증 |
| 피드백 API | E2E 테스트 (피드백 생성 → 조회 → 통계) |

### 회귀 테스트

- `golden-dataset.json` 10건에 대해 Phase별 답변 품질 비교
- 기준: faithfulness score ≥ 0.70, citation accuracy ≥ 90%
- 비용: Phase 1 완료 후 30% 이상 절감 달성 여부

---

## 7. 리스크 & 완화

| 리스크 | 영향 | 완화 |
|--------|------|------|
| 증거 수 제한으로 답변 품질 하락 | 중요 증거 누락 가능 | min-score 임계값 조정, 피드백 루프로 모니터링 |
| Critic 스킵 증가로 할루시네이션 증가 | 팩트체크 누락 | SelfReview(규칙 기반)는 항상 실행, 피드백으로 모니터링 |
| Semantic Cache 오염 | 잘못된 캐시 답변 제공 | 높은 유사도 임계값(0.95), 문서 변경 시 자동 무효화 |
| 토큰 예산 부족으로 품질 저하 | 필요한 단계 스킵 | 필수 단계는 예산 면제, 예산 상한 점진적 조정 |

---

## 부록: 현재 코드 아키텍처 강점

개선이 필요하지만, 현재 아키텍처가 가진 강점도 기록:

1. **3-level Fallback Retrieval**: 정확→범주→필터없음 단계적 확장으로 검색 실패 최소화
2. **Parent-Child 이중 청킹**: CHILD로 정밀 매칭, PARENT로 문맥 전달 — 검색/이해 분리
3. **RRF Hybrid Search**: 시맨틱 + 키워드 퓨전으로 단일 모달리티 편향 방지
4. **SelfReview (규칙 기반)**: LLM 비용 없이 할루시네이션/인용 오류 탐지
5. **모델 티어 분리**: Heavy/Medium/Light 3단계로 작업 복잡도별 비용 최적화
6. **Provider 패턴**: Mock/Real 이중 구현으로 OpenAI 없이도 개발/테스트 가능
