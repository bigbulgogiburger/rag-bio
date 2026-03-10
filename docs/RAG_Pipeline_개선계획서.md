# RAG Pipeline 핵심 개선 계획서

> 작성일: 2026-03-11
> 작성자: RAG Pipeline & AI Agent 전문가
> 목표: **품질 향상 + 비용 절감 (현재 대비 -50% 이상)**

---

## 1. 현황 진단

### 1.1 현재 파이프라인 흐름

```
질문 입력
  → QueryTranslation (ko→en, Light 모델)
  → HyDE (가상 답변 생성, Light 모델)
  → Vector Search (Qdrant)
  → Keyword Search (PostgreSQL ts_vector)
  → RRF Fusion (K=60)
  → Pointwise Reranking (Medium 모델 × N건)  ← 최대 비용 병목
  → AdaptiveRetrieval (Medium 모델)
  → MultiHop (Medium 모델, 최대 3회)
  → Verify (Medium 모델)
  → Compose (Heavy 모델)
  → Critic (Heavy 모델, 최대 2회)
  → SelfReview (Heavy 모델)
  → SSE 전송
```

### 1.2 비용 분석 (문의 1건 기준)

| 단계 | 모델 | API 호출 수 | 예상 비용 | 비중 |
|------|------|------------|----------|------|
| QueryTranslation | Light (gpt-5-nano) | 1 | $0.001 | 1% |
| HyDE | Light (gpt-5-nano) | 1 | $0.002 | 3% |
| **Reranking (Pointwise)** | **Medium (gpt-5-mini)** | **20~50** | **$0.040** | **50%** |
| AdaptiveRetrieval | Medium (gpt-5-mini) | 1 | $0.002 | 3% |
| MultiHop | Medium (gpt-5-mini) | 1~3 | $0.004 | 5% |
| SearchToolAgent | Medium (gpt-5-mini) | 1 | $0.002 | 3% |
| Verify | Medium (gpt-5-mini) | 1 | $0.003 | 4% |
| Compose | Heavy (gpt-5-mini) | 1 | $0.010 | 13% |
| Critic | Heavy (gpt-5-mini) | 1~2 | $0.008 | 10% |
| SelfReview | Heavy (gpt-5-mini) | 1 | $0.006 | 8% |
| **합계** | | **30~60+** | **~$0.080** | **100%** |

### 1.3 핵심 문제점

| # | 문제 | 영향 | 심각도 |
|---|------|------|--------|
| P1 | **Pointwise 리랭킹 (N회 LLM 호출)** | 비용 50%, 레이턴시 10~20초 | CRITICAL |
| P2 | **SSE Broken Pipe → 파이프라인 크래시** | 답변 저장 실패, 전체 작업 손실 | CRITICAL |
| P3 | **KB 제품군 미전파** | 제품 필터링 무효화, 무관한 문서 검색 | HIGH |
| P4 | **한국어 키워드 검색 무효** | Hybrid Search에서 keyword 기여 0% | HIGH |
| P5 | **RRF 스코어 압축** | 0.016 수준의 비직관적 신뢰도 | MEDIUM |
| P6 | **HyDE 항상 실행** | 단순 질문에도 불필요한 LLM 호출 | MEDIUM |
| P7 | **임베딩 캐시 부재** | 동일 문서 재인덱싱 시 중복 비용 | LOW |

---

## 2. 개선 계획 (우선순위 순)

### Phase 1: 비용 절감 + 안정성 (CRITICAL)

#### TASK 2-1: Listwise 배치 리랭킹 (비용 -45%)
**현재**: 20~50건 각각 LLM 호출 (Pointwise)
**개선**: 1회 LLM 호출로 전체 후보 일괄 랭킹 (Listwise)

```
AS-IS: for each candidate → LLM("이 문서는 관련성 1~10?") → 50 API calls
TO-BE: LLM("아래 20개 문서를 관련성 순으로 정렬해줘") → 1 API call
```

- **구현**: `OpenAiListwiseRerankingService` 신규 생성
- **프롬프트**: 후보 문서 리스트 + 질문 → JSON 배열로 순위 반환
- **Fallback**: 파싱 실패 시 RRF 스코어 순서 유지
- **비용 효과**: $0.040 → $0.003 (1회 호출, -92%)
- **품질**: Listwise는 Pointwise 대비 95%+ 동등 성능 (연구 결과)

#### TASK 2-2: 파이프라인-SSE 디커플링 (안정성)
**현재**: SSE 연결 끊김 → IOException → 파이프라인 중단 → 답변 미저장
**개선**: 파이프라인을 독립 실행, SSE는 옵저버 패턴으로 분리

```
AS-IS: Pipeline → SSE.send() → IOException → Pipeline CRASH
TO-BE: Pipeline → EventBus.publish() → [SSE Observer tries send, ignores errors]
                                      → Pipeline completes independently
                                      → Answer always saved to DB
```

- **구현**: `AnswerOrchestrationService`에서 SSE 직접 호출 제거
- **패턴**: `ApplicationEventPublisher` + `@EventListener`로 느슨한 결합
- **핵심**: 파이프라인 성공/실패와 SSE 전송을 완전히 분리

#### TASK 2-3: KB 제품군 전파 수정 (검색 품질)
**현재**: `KnowledgeIndexingWorker.indexOneAsync()`가 productFamily를 청킹에 미전달
**개선**: KB 문서의 productFamily를 chunk metadata와 벡터 스토어에 전파

- **수정 파일**: `KnowledgeIndexingWorker.java`, `ChunkingService.java`
- **마이그레이션**: 기존 KB 청크의 productFamily 업데이트 SQL

### Phase 2: 검색 품질 개선 (HIGH)

#### TASK 2-4: 한국어 키워드 검색 개선
**현재**: PostgreSQL `simple` 사전 → 한국어 형태소 분석 불가 → keyword 결과 항상 0
**개선**: `pg_bigm` (바이그램 인덱스) 도입으로 한국어 부분 매칭 지원

```sql
-- pg_bigm 기반 검색 (한국어 지원)
CREATE INDEX idx_chunks_content_bigm ON document_chunks
  USING gin (content gin_bigm_ops);

SELECT * FROM document_chunks
WHERE content LIKE '%QX200%'  -- pg_bigm이 바이그램 인덱스로 가속
```

- **대안**: pg_bigm 설치 불가 시 → Application-level 한국어 토크나이저 (Komoran/Nori)
- **Fallback**: ILIKE 기반 검색 (인덱스 없이도 동작)
- **RDS 호환**: Amazon RDS는 pg_bigm 미지원 → Application-level 접근 우선

#### TASK 2-5: 스코어 정규화
**현재**: RRF K=60 → 스코어 0.008~0.016 (비직관적)
**개선**: 파이프라인 전체에서 0.0~1.0 정규화된 스코어 사용

```
AS-IS: RRF score = 1/(60+rank) ≈ 0.016
TO-BE: Normalized = (score - min) / (max - min) → 0.0 ~ 1.0
```

- **적용 위치**: `HybridSearchService.fuseScores()` 후 정규화
- **Verify 단계**: 정규화 스코어 기반으로 verdict 임계값 재조정
- **UI 표시**: 프론트엔드 신뢰도 표시 직관성 향상

#### TASK 2-6: 지능형 파이프라인 라우팅 (비용 -20%)
**현재**: 모든 질문이 전체 파이프라인 통과 (7+ LLM 호출)
**개선**: 검색 결과 신뢰도 기반으로 불필요한 단계 스킵

```
IF top_rerank_score > 0.85 AND score_gap > 0.3:
    SKIP MultiHop, SKIP Critic  → 비용 -30%
ELIF top_rerank_score < 0.3:
    답변에 "추가 자료 필요" 플래그 → SKIP Compose entirely → 비용 -50%
ELSE:
    Full pipeline (현재와 동일)
```

- **구현**: `AnswerOrchestrationService`에 라우팅 로직 추가
- **안전장치**: 라우팅 결과를 audit 로그에 기록

### Phase 3: 효율 최적화 (MEDIUM)

#### TASK 2-7: 임베딩 캐시
**현재**: 동일 쿼리도 매번 OpenAI Embedding API 호출
**개선**: In-memory LRU 캐시 (TTL 30분, 최대 500엔트리)

- **적용**: `EmbeddingService.embedQuery()` 래핑
- **캐시 키**: SHA-256(query_text)
- **비용 효과**: 반복 질문 시 임베딩 비용 0, 약 20~30% 절감

#### TASK 2-8: Critic/SelfReview 조건부 실행 (비용 -10%)
**현재**: 항상 Critic + SelfReview 실행 (2~3 Heavy LLM 호출)
**개선**: Verify 신뢰도 높으면 Critic 1회만, 매우 높으면 스킵

```
IF verify_confidence > 0.9:
    SKIP Critic, SKIP SelfReview
ELIF verify_confidence > 0.7:
    Critic 1회만 (maxIterations=1)
ELSE:
    Full Critic + SelfReview (현재와 동일)
```

---

## 3. 예상 비용 효과

| 개선 항목 | 현재 비용 | 개선 후 | 절감률 |
|-----------|----------|---------|--------|
| Reranking (Listwise) | $0.040 | $0.003 | -92% |
| 파이프라인 라우팅 | $0.020 | $0.012 | -40% |
| Critic 조건부 실행 | $0.014 | $0.008 | -43% |
| 임베딩 캐시 | $0.003 | $0.002 | -33% |
| **합계** | **$0.080** | **$0.028** | **-65%** |

---

## 4. 개발 분배 (Agent Teams, 8명)

| Agent | 담당 TASK | 주요 파일 | 예상 시간 |
|-------|----------|----------|----------|
| Agent 1 | TASK 2-1: Listwise 리랭킹 | `OpenAiListwiseRerankingService.java` (신규), `RerankingService.java` | 2h |
| Agent 2 | TASK 2-2: SSE 디커플링 | `AnswerOrchestrationService.java`, `PipelineEvent.java` (신규) | 2h |
| Agent 3 | TASK 2-3: KB 제품군 전파 | `KnowledgeIndexingWorker.java`, `ChunkingService.java`, V34 마이그레이션 | 1h |
| Agent 4 | TASK 2-4: 한국어 검색 개선 | `KeywordSearchService.java` (신규/수정), SQL | 2h |
| Agent 5 | TASK 2-5: 스코어 정규화 | `HybridSearchService.java`, `AnalysisService.java` | 1h |
| Agent 6 | TASK 2-6: 파이프라인 라우팅 | `AnswerOrchestrationService.java` (Agent 2와 조율) | 1.5h |
| Agent 7 | TASK 2-7: 임베딩 캐시 | `EmbeddingService.java`, `EmbeddingCache.java` (신규) | 1h |
| Agent 8 | TASK 2-8: Critic 조건부 실행 | `OpenAiCriticAgent.java`, `AnswerOrchestrationService.java` | 1h |

### 파일 충돌 방지 전략
- `AnswerOrchestrationService.java`: Agent 2 (SSE 디커플링) 먼저 → Agent 6 (라우팅), Agent 8 (Critic) 이후
- 나머지 Agent들은 독립 파일 작업으로 병렬 실행 가능
- **Phase 1 (1~3) → Phase 2 (4~6) → Phase 3 (7~8)** 순서

---

## 5. 품질 보증

- 각 TASK별 단위 테스트 작성
- Listwise 리랭킹: Pointwise 대비 NDCG@10 동등성 검증
- SSE 디커플링: Broken pipe 시나리오 테스트
- 스코어 정규화: 기존 verdict 임계값과 정합성 확인
- 기존 테스트 전체 통과 확인 (`./gradlew build`)
