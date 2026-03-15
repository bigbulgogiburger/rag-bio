# RAG Pipeline 격차 분석 및 해소 방안 v2

> 작성일: 2026-03-15 | 작성자: RAG Pipeline & AI Agent 전문가 팀 (8인)
> 대상: Bio-Rad CS RAG Hub — pienClaw 프로젝트

---

## 1. 현재 파이프라인 아키텍처 (As-Is)

### 1.1 8단계 RAG Pipeline

```
DECOMPOSE → RETRIEVE(3-Level) → ADAPTIVE_RETRIEVE → MULTI_HOP
    → VERIFY → COMPOSE → CRITIC → SELF_REVIEW
```

### 1.2 보유 기술 스택

| 영역 | 구현 상태 | 핵심 기술 |
|------|----------|----------|
| 질문 분해 | ✅ 완료 | LLM 기반 sub-question 분리, ProductFamily 추출 |
| 검색 | ✅ 완료 | HybridSearch (Vector + Keyword), 3-Level Fallback, Product 필터 |
| 쿼리 변환 | ✅ 완료 | HyDE (Hypothetical Document Embedding) |
| 리랭킹 | ✅ 완료 | Cross-Encoder Reranking |
| 적응적 검색 | ✅ 완료 | Adaptive Retrieval Agent (3 query variants) |
| 멀티홉 | ✅ 완료 | 2-hop 후속 검색 (낮은 신뢰도/증거 부족 시) |
| 검증 | ✅ 완료 | Verdict 분류 (SUPPORTED/CONDITIONAL/REFUTED), 위치가중 점수 |
| 답변 생성 | ✅ 완료 | 증거 기반 한국어 답변, 토큰 기반 증거 예산 |
| 비평 | ✅ 완료 | Faithfulness 평가 + 재작성 (최대 2회) |
| 자기 검토 | ✅ 완료 | 규칙 기반 품질 검사 (중복, 팬텀, 수치, 인용 오류) |
| 청킹 | ✅ 완료 | Parent-Child 이중 청킹, Contextual Enrichment |
| 임베딩 | ✅ 완료 | text-embedding-3-large (3072차원) |
| 비용 제어 | ✅ 완료 | TokenBudgetManager (문의당 25K 토큰 한도) |
| 이미지 분석 | ✅ 완료 | VLM 기반 이미지 → 청크 인덱싱 |
| 시맨틱 캐시 | ✅ 완료 | 유사 질문 캐시 재활용 |

### 1.3 이번 스프린트에서 추가된 기능

| 기능 | 설명 |
|------|------|
| **Email-Style Answer Output** | TipTap Rich Text Editor, Gmail 호환 인라인 HTML, 이미지 업로드/서빙 |
| **Pipeline Status Persistence** | 서버 사이드 파이프라인 상태 추적, 탭 전환/새로고침 후 복원 |
| **Draft Format Transition** | TEXT/HTML 이중 포맷 지원, 에디터 전환 기반 |

---

## 2. 최신 RAG 기술 동향 (2025-2026 State-of-the-Art)

### 2.1 Agentic RAG (에이전틱 RAG)

단순 검색-생성을 넘어 **자율적으로 계획하고 도구를 사용하는** 에이전트 기반 RAG.

| 기법 | 설명 | 대표 논문/프로젝트 |
|------|------|-------------------|
| **Tool-Augmented RAG** | 검색 외에 계산기, API 호출, DB 쿼리 등 도구 사용 | Toolformer, Gorilla |
| **Planning-Based RAG** | 복잡한 질문에 대해 검색 전략을 먼저 수립 | ReAct, Plan-and-Solve |
| **Router RAG** | 질문 유형별 최적 파이프라인 자동 라우팅 | Semantic Router |
| **Iterative RAG** | 답변 부족 시 자동으로 추가 검색 반복 | IRCoT |

### 2.2 Self-Reflective RAG

| 기법 | 설명 |
|------|------|
| **Self-RAG** | 검색 필요성 판단 → 선택적 검색 → 증거 관련성 평가 → 답변 생성 |
| **CRAG (Corrective RAG)** | 검색 결과 품질 평가 → Correct/Incorrect/Ambiguous 분류 → 웹 검색 보완 |
| **Speculative RAG** | 다수의 초안 병렬 생성 → 검증 모델이 최적 초안 선택 |

### 2.3 Graph RAG & Structured Knowledge

| 기법 | 설명 |
|------|------|
| **Microsoft GraphRAG** | 문서에서 엔티티/관계 추출 → 지식 그래프 → 커뮤니티 요약 → 글로벌 질문 대응 |
| **KG-RAG** | 벡터 검색 + 지식 그래프 트래버설 병합 |
| **Hierarchical Index** | 요약 → 상세 2단계 인덱스로 점진적 탐색 |

### 2.4 Advanced Retrieval Techniques

| 기법 | 설명 | 우리 현황 |
|------|------|----------|
| **RAG Fusion** | 다중 쿼리 변형 → RRF(Reciprocal Rank Fusion) 병합 | 부분 구현 (Adaptive만) |
| **RAPTOR** | 재귀적 추상화 → 계층적 트리 구조 인덱싱 | 미구현 |
| **Late Chunking** | 임베딩 시점에 문서 전체 컨텍스트 반영 후 분할 | 미구현 |
| **ColBERT** | Token-level late interaction으로 정밀 매칭 | 미구현 (Cross-Encoder 사용) |
| **Contextual Retrieval** | Anthropic 방식: 청크에 문서 컨텍스트 프리픽스 추가 | ✅ 구현 (Sprint 3) |
| **Fine-tuned Embeddings** | 도메인 특화 임베딩 모델 파인튜닝 | 미구현 |

### 2.5 Multi-Modal & Real-Time RAG

| 기법 | 설명 |
|------|------|
| **Vision RAG** | 이미지/차트/테이블을 직접 이해하는 VLM 기반 검색 |
| **Streaming RAG** | 답변을 토큰 단위로 스트리밍 (SSE/WebSocket) |
| **Real-Time Index** | 문서 변경 시 즉시 인덱스 업데이트 |

### 2.6 Evaluation & Observability

| 기법 | 설명 |
|------|------|
| **RAGAS** | Faithfulness, Answer Relevancy, Context Precision/Recall 자동 평가 |
| **LangSmith/Phoenix** | 파이프라인 트레이싱, 단계별 비용/지연시간 모니터링 |
| **A/B Testing** | 파이프라인 변형 간 실시간 성능 비교 |

---

## 3. 격차 분석 (Gap Analysis)

### 3.1 핵심 격차 매트릭스

| # | 격차 영역 | 심각도 | 현재 수준 | 목표 수준 | 비즈니스 임팩트 |
|---|----------|--------|----------|----------|---------------|
| G1 | **Streaming 답변 생성** | 🔴 HIGH | 없음 (blocking call) | 토큰 단위 스트리밍 | UX 체감 속도 3~5x 향상 |
| G2 | **Query Router** | 🟡 MEDIUM | 단일 파이프라인 | 질문 유형별 경로 분기 | 단순 문의 응답 시간 50% 단축 |
| G3 | **RAG Fusion (RRF)** | 🟡 MEDIUM | Adaptive만 (0건 시) | 항상 다중 쿼리 + RRF 병합 | 검색 재현율(Recall) 15-25% 향상 |
| G4 | **GraphRAG** | 🟡 MEDIUM | 없음 | 제품-부품-증상 지식 그래프 | 글로벌/비교 질문 대응 가능 |
| G5 | **Fine-tuned Embeddings** | 🟡 MEDIUM | 범용 모델 | Bio-Rad 도메인 파인튜닝 | 검색 정밀도 10-20% 향상 |
| G6 | **RAGAS 자동 평가** | 🟡 MEDIUM | 수동 평가 | CI/CD 통합 자동 평가 | 회귀 방지, 품질 정량화 |
| G7 | **Self-RAG 선택적 검색** | 🟢 LOW | 항상 검색 | 검색 필요성 사전 판단 | 단순 인사말 등 불필요한 검색 회피 |
| G8 | **Late Chunking** | 🟢 LOW | Parent-Child + Contextual | 문서 전체 컨텍스트 임베딩 | 문맥 손실 최소화 |
| G9 | **Real-Time Index** | 🟢 LOW | 배치 인덱싱 | 문서 변경 시 즉시 반영 | 최신 문서 즉시 검색 가능 |
| G10 | **Observability Dashboard** | 🟢 LOW | 토큰 추적 기본 | LangSmith급 트레이싱 | 파이프라인 병목 실시간 파악 |

### 3.2 이미 우수한 영역

| 영역 | 우리 구현 | 업계 대비 수준 |
|------|----------|--------------|
| **Multi-Step Pipeline** | 8단계 풀 파이프라인 | ⭐⭐⭐⭐⭐ 최상위권 |
| **Hybrid Search** | Vector + BM25 + Product Filter | ⭐⭐⭐⭐ 우수 |
| **Contextual Chunking** | Parent-Child + Enrichment | ⭐⭐⭐⭐ 우수 |
| **HyDE + Reranking** | Cross-Encoder 리랭킹 | ⭐⭐⭐⭐ 우수 |
| **Adaptive Retrieval** | 0건 시 3-variant 재검색 | ⭐⭐⭐⭐ 우수 |
| **Multi-Hop** | 2-hop 후속 검색 | ⭐⭐⭐⭐ 우수 |
| **Critic + Self-Review** | LLM 비평 + 규칙 기반 검토 | ⭐⭐⭐⭐ 우수 |
| **Cost Control** | TokenBudgetManager | ⭐⭐⭐⭐ 우수 |
| **Email-Ready Output** | TipTap + Gmail HTML | ⭐⭐⭐⭐ 우수 (이번 스프린트) |
| **Pipeline Observability** | 서버 사이드 상태 추적 | ⭐⭐⭐ 양호 (이번 스프린트) |

---

## 4. 해소 방안 (Roadmap)

### 4.1 Phase 1: 즉시 적용 가능 (1-2주)

#### G1: Streaming 답변 생성
```
현재: POST /answers/draft → 전체 답변 반환 (10-30초 대기)
목표: SSE로 토큰 단위 스트리밍
```

**구현 방안:**
1. OpenAI API `stream: true` 옵션 활성화
2. Compose Step에서 SSE 이벤트로 토큰 실시간 전송
3. 프론트엔드: 에디터에 실시간 텍스트 삽입
4. 나머지 단계(검색/검증)는 기존 파이프라인 이벤트 유지

**예상 효과:** 첫 토큰 표시까지 2-3초, 체감 속도 3-5x 향상

#### G6: RAGAS 자동 평가
```
현재: 수동 평가 (스프린트 평가 보고서)
목표: CI 파이프라인에 자동 품질 게이트
```

**구현 방안:**
1. 평가 데이터셋 구축 (50-100 질문-답변 쌍)
2. RAGAS 메트릭 산출: Faithfulness, Answer Relevancy, Context Precision
3. GitHub Actions에서 PR마다 자동 평가 실행
4. 기준선 이하 시 PR 블로킹

### 4.2 Phase 2: 중기 개선 (3-4주)

#### G2: Query Router
```
현재: 모든 질문 → 동일한 8단계 파이프라인
목표: 질문 유형별 최적 경로
```

**라우팅 맵:**
| 질문 유형 | 파이프라인 | 예시 |
|----------|----------|------|
| 단순 사실 | RETRIEVE → COMPOSE | "QX200 전원 전압은?" |
| 비교/분석 | 풀 8단계 | "QX200과 QX600 차이점" |
| 문제 해결 | DECOMPOSE → MULTI_HOP → COMPOSE | "드롭렛 생성 안 됨" |
| 일반 인사 | COMPOSE only (검색 스킵) | "안녕하세요" |

**구현:** Light LLM 분류기 (gpt-4o-mini) → enum 라우팅

#### G3: RAG Fusion (RRF)
```
현재: 단일 쿼리 + HyDE (0건 시만 Adaptive 3-variant)
목표: 항상 3-5개 쿼리 변형 + RRF 병합
```

**구현 방안:**
1. 모든 검색에서 원본 + HyDE + 2개 변형 쿼리 생성
2. 각 쿼리 결과를 Reciprocal Rank Fusion으로 병합
3. 병합 후 Cross-Encoder 리랭킹 적용
4. 기존 Adaptive Retrieve는 RRF 이후 0건 시만 발동

### 4.3 Phase 3: 장기 혁신 (1-2개월)

#### G4: GraphRAG (Bio-Rad 제품 지식 그래프)
```
현재: 플랫 청크 기반 검색
목표: 제품 → 부품 → 증상 → 해결책 그래프
```

**엔티티 스키마:**
```
Product ─[HAS_COMPONENT]→ Component
Product ─[HAS_SYMPTOM]→ Symptom
Symptom ─[RESOLVED_BY]→ Solution
Solution ─[REFERENCES]→ DocumentChunk
Product ─[COMPATIBLE_WITH]→ Consumable
```

**활용 시나리오:**
- "QX200 관련 모든 문제와 해결책" → 그래프 트래버설
- "드롭렛 생성기와 호환되는 소모품" → 관계 탐색
- 여러 제품 비교 → 커뮤니티 요약

#### G5: Fine-tuned Embeddings
```
현재: text-embedding-3-large (범용)
목표: Bio-Rad 기술 문서 파인튜닝
```

**방법:**
1. Bio-Rad 문서에서 (질문, 관련 청크) 쌍 추출 (1000+)
2. Contrastive Learning으로 embedding 모델 파인튜닝
3. 평가: 검색 정밀도/재현율 before/after 비교

---

## 5. 우선순위 매트릭스

```
높은 임팩트
    │
    │  ┌─────────┐   ┌──────────┐
    │  │G1 Stream│   │G4 GraphRAG│
    │  │  (2주)  │   │  (6주)   │
    │  └─────────┘   └──────────┘
    │  ┌─────────┐   ┌──────────┐
    │  │G2 Router│   │G5 FineTune│
    │  │  (2주)  │   │  (4주)   │
    │  └─────────┘   └──────────┘
    │  ┌─────────┐   ┌──────────┐
    │  │G3 Fusion│   │G10 Observ│
    │  │  (2주)  │   │  (3주)   │
    │  └─────────┘   └──────────┘
    │  ┌─────────┐
    │  │G6 RAGAS │
    │  │  (1주)  │
    │  └─────────┘
    │
낮은 임팩트 ───────────────────────── 높은 난이도
    │  ┌─────────┐   ┌──────────┐
    │  │G7 SelfRAG│  │G8 LateCnk│
    │  │  (1주)  │   │  (3주)   │
    │  └─────────┘   └──────────┘
    │               ┌──────────┐
    │               │G9 RTIndex│
    │               │  (2주)   │
    │               └──────────┘
```

---

## 6. 권장 실행 순서

| 순서 | 격차 | 소요 | ROI |
|------|------|------|-----|
| 1 | **G1: Streaming 답변** | 2주 | ⭐⭐⭐⭐⭐ |
| 2 | **G6: RAGAS 자동 평가** | 1주 | ⭐⭐⭐⭐ |
| 3 | **G2: Query Router** | 2주 | ⭐⭐⭐⭐ |
| 4 | **G3: RAG Fusion (RRF)** | 2주 | ⭐⭐⭐⭐ |
| 5 | **G4: GraphRAG** | 6주 | ⭐⭐⭐⭐ |
| 6 | **G5: Fine-tuned Embeddings** | 4주 | ⭐⭐⭐ |
| 7 | **G7: Self-RAG** | 1주 | ⭐⭐⭐ |
| 8 | **G10: Observability** | 3주 | ⭐⭐⭐ |
| 9 | **G8: Late Chunking** | 3주 | ⭐⭐ |
| 10 | **G9: Real-Time Index** | 2주 | ⭐⭐ |

---

## 7. 결론

### 현재 강점
pienClaw의 RAG 파이프라인은 **8단계 풀 파이프라인, HyDE + Cross-Encoder 리랭킹, Contextual Parent-Child 청킹, Adaptive/Multi-Hop 검색, Critic + Self-Review 이중 검증, TokenBudget 비용 제어**를 갖추고 있어 업계 상위 수준입니다.

이번 스프린트에서 **Email-Style Output (TipTap + Gmail 호환)** 과 **Pipeline Status Persistence (서버 사이드 상태 추적)** 를 추가하여 프로덕션 UX를 크게 개선했습니다.

### 핵심 격차
가장 큰 격차는 **Streaming 답변 생성 (G1)** 과 **Query Router (G2)** 입니다.
- G1은 사용자 체감 속도를 즉시 개선하여 가장 높은 ROI를 제공합니다.
- G2는 단순 문의의 응답 시간과 비용을 대폭 절감합니다.

### 중장기 방향
**GraphRAG (G4)** 와 **Fine-tuned Embeddings (G5)** 는 Bio-Rad 도메인 특화 최적화로, 제품 간 관계 탐색과 기술 용어 이해도를 한 단계 끌어올릴 수 있습니다.

> 📌 **다음 스프린트 권장**: G1 (Streaming) + G6 (RAGAS) → 2주 내 완료 가능, 가장 높은 즉시 임팩트
