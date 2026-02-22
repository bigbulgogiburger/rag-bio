# PRD v6: 제품군 기반 지능형 검색 필터링

> **Version**: 6.0
> **Date**: 2026-02-22
> **Status**: Draft
> **Authors**: RAG Pipeline Expert + BigTech Agile PM (AI-assisted)

---

## 1. 배경 및 문제 정의

### 1.1 사용자 피드백

> "어제보다 답변에 대한 정답률이 굉장히 높아졌어요. 다만, 동일 단어가 질문에 들어있을 때 업로드한 매뉴얼 모두를 바탕으로 답을 제공하는 부분은 여전히 확인됩니다."

### 1.2 핵심 문제

고객 질문에는 보통 **1~3개의 특정 제품군**이 포함됨. 그러나 현재 시스템은 질문의 키워드가 여러 매뉴얼에 등장하면 **모든 제품군의 매뉴얼을 검색**하여 관련 없는 정보까지 답변에 포함시킴.

### 1.3 근본 원인 분석 (코드 레벨)

| Gap | 원인 | 코드 위치 |
|-----|------|----------|
| **단일 제품군만 추출** | `ProductExtractorService.extract()`가 `bestMatch` 하나만 반환 | `ProductExtractorService.java:65-86` |
| **단일 값 필터 구조** | `SearchFilter.productFamily`가 `String` 단일 값 | `SearchFilter.java` |
| **품질 기반 Fallback 부재** | 0건일 때만 fallback 발동, 품질 미달 기준 없음 | `AnswerOrchestrationService.java:97-110` |
| **메타데이터 비정규화** | KB 문서 `productFamily`가 자유 텍스트 입력 | `KnowledgeDocumentJpaEntity.java`, `FileQueueItem.tsx` |
| **Chunk 레벨 productFamily 누락** | `ChunkingService`가 chunk 생성 시 productFamily 미설정 | `ChunkingService.java` |

### 1.4 현재 파이프라인 흐름

```
질문 입력
  → QuestionDecomposerService.decompose()     — 복합 질문 분해
  → ProductExtractorService.extract()          — 단일 제품 추출 ❌
  → SearchFilter.forProduct(single)            — 단일 필터 ❌
  → HybridSearchService.search()
     → VectorStore.search() + KeywordSearch()
     → RRF 결과 융합
  → 0건이면 fallback (전체 검색)               — 급격한 fallback ❌
  → VerifyStep → ComposeStep → SelfReview
```

---

## 2. 목표

### 2.1 개선 후 파이프라인

```
질문 입력
  │
  ├─ QuestionDecomposerService.decompose()
  │     → SubQuestion[] (각각 context/productFamilies 포함)
  │
  ├─ ProductExtractorService.extractAll()          ✅ 1~3개 제품군
  │     → ExtractedProducts { [naica, CFX], confidence, "RULE" }
  │
  ├─ SearchFilter.forProducts(inquiryId, {"naica", "CFX"})  ✅ Set<String>
  │
  ├─ [복합 질문] retrievePerQuestion()
  │     SubQ1: "naica 10x Multiplex 사용법"
  │       → HybridSearch(filter={naica})        → 5건
  │       → RetrievalQuality.evaluate()         → SUFFICIENT ✅
  │     SubQ2: "CFX96 프로토콜 설정"
  │       → HybridSearch(filter={CFX})          → 3건
  │       → RetrievalQuality.evaluate()         → MARGINAL
  │       → Level 1: expand(CFX → REAL_TIME_PCR) → +2건  ✅ 점진적 확장
  │       → RetrievalQuality.evaluate()         → SUFFICIENT
  │
  ├─ deduplicateEvidences()
  │
  ├─ VerifyStep.execute()
  │
  └─ ComposeStep.execute()
```

### 2.2 성공 지표

| KPI | 현재 (추정) | Sprint 2 목표 | Sprint 3 목표 |
|-----|-----------|--------------|--------------|
| 검색 정밀도 (Precision) | 60~70% | 85%+ | 90%+ |
| 오답 혼입률 | 30~40% | 10% 미만 | 5% 미만 |
| 제품군 자동 인식 정확도 | 미측정 | 80%+ | 90%+ |
| 상담원 답변 수정 시간 | 기준값 | 30% 단축 | 50% 단축 |
| 검색 재현율 (Recall) | 80%+ | 80%+ 유지 | 85%+ |

---

## 3. 페르소나 및 사용자 스토리

### 3.1 페르소나: CS 상담원 "김선영"

| 항목 | 설명 |
|------|------|
| 역할 | Bio-Rad Korea 기술 지원 CS 상담원 (경력 3년) |
| 일일 문의량 | 15~25건 |
| Pain Point | "QX200 droplet generation 질문인데, CFX96 매뉴얼 내용까지 답변에 섞여서 하나하나 걸러내야 해요" |
| 기대 결과 | 질문에 언급된 제품 문서만 검색되고, 관련 없는 정보가 답변에 포함되지 않는 것 |

### 3.2 질문 유형 분류

| 유형 | 비율 | 예시 | 필요 검색 범위 |
|------|------|------|---------------|
| 단일 제품 | 60% | "QX200에서 droplet generation 실패 시 troubleshooting은?" | 해당 제품군만 |
| 다중 제품 (1~3개) | 20% | "naica와 QX ONE의 multiplexing 차이점은?" | 언급된 2~3개 제품군 |
| 제품 비교 | 10% | "ddPCR과 qPCR 중 어떤 방식이 적합한가요?" | 비교 대상 + 일반 프로토콜 |
| 일반 문의 | 10% | "assay 설계 시 primer 농도 최적화 방법은?" | 전체 (제품 무관) |

### 3.3 사용자 스토리

| ID | 스토리 | 우선순위 |
|----|--------|---------|
| US-1 | CS 상담원으로서, 질문에 포함된 제품명(1~3개)을 시스템이 자동 식별하여 해당 제품 문서만 우선 검색하기를 원한다 | **Must** |
| US-2 | CS 상담원으로서, 자동 추출된 제품군 태그를 확인하고 수정/추가할 수 있기를 원한다 | **Must** |
| US-3 | KB 관리자로서, 문서 업로드 시 표준 제품군 목록에서 선택하여 메타데이터를 관리하기를 원한다 | **Should** |
| US-4 | CS 상담원으로서, 답변에서 어떤 제품군 문서가 사용되었는지 확인할 수 있기를 원한다 | **Should** |
| US-5 | CS 상담원으로서, 필터로 근거 부족 시 시스템이 점진적으로 범위를 확장하되 확장 사실을 알려주길 원한다 | **Could** |

---

## 4. 기술 설계

### 4.1 Query Understanding Layer

#### 방안 비교

| 항목 | Rule-based (현재) | LLM-based | Hybrid (제안) |
|------|-------------------|-----------|---------------|
| 레이턴시 | <1ms | 200~500ms | <1ms + LLM fallback |
| 정확도 | 높음 (정확한 제품명) | 매우 높음 (맥락 이해) | 높음 |
| 멀티 제품 | 현재 불가 | 가능 | 가능 |
| 비용 | 0 | OpenAI API 과금 | 최소 |

#### 제안: Hybrid 방식

**Phase 1 (Rule-based 확장)**: `ProductExtractorService`를 `List<ExtractedProduct>` 반환으로 변경. 모든 매칭을 수집.

```java
public record ExtractedProducts(
    List<ExtractedProduct> products,     // 0~3개
    double overallConfidence,
    String strategy                      // "RULE", "LLM", "HYBRID"
) {
    public List<String> productFamilies() {
        return products.stream()
            .map(ExtractedProduct::productFamily)
            .distinct().toList();
    }
}
```

**Phase 2 (LLM Enrichment)**: Rule-based로 추출 실패 시 LLM에 제품군 추출 요청.

### 4.2 제품군 분류 체계 (2-Level)

```
ProductCategory (대분류)
  ├── DIGITAL_PCR     → naica, vericheck, QX, ddPCR
  ├── REAL_TIME_PCR   → CFX
  ├── IMMUNOASSAY     → BioPlex
  ├── IMAGING         → ChemiDoc
  ├── CHROMATOGRAPHY  → NGC
  └── ELECTROPHORESIS → MiniPROTEAN, TransBlot
```

이를 통해 관련 제품군 간 "근접 검색(neighbor search)" 가능. 예: naica 결과 미달 시 같은 DIGITAL_PCR 카테고리의 QX, ddPCR 문서로 확장.

### 4.3 SearchFilter 복수 제품군 지원

```java
// 변경 전
public record SearchFilter(
    UUID inquiryId,
    Set<UUID> documentIds,
    String productFamily,           // 단일 값 ❌
    Set<String> sourceTypes
)

// 변경 후
public record SearchFilter(
    UUID inquiryId,
    Set<UUID> documentIds,
    Set<String> productFamilies,    // 복수 값 ✅
    Set<String> sourceTypes
)
```

### 4.4 Filtered Retrieval 구현

#### Vector Store (Qdrant)

```java
// 변경 전: match.value (단일)
mustClauses.add(Map.of("key", "productFamily",
    "match", Map.of("value", filter.productFamily())));

// 변경 후: match.any (복수)
mustClauses.add(Map.of("key", "productFamily",
    "match", Map.of("any", new ArrayList<>(filter.productFamilies()))));
```

#### Keyword Search (PostgreSQL)

```java
// 변경 전: = ?
sql.append(" AND product_family = ?");

// 변경 후: IN (?,?,?)
String placeholders = String.join(",",
    filter.productFamilies().stream().map(s -> "?").toList());
sql.append(" AND product_family IN (").append(placeholders).append(")");
```

### 4.5 3단계 Fallback 메커니즘

```
Level 0: 제품군 필터 적용 검색
  → SUFFICIENT (max≥0.60, relevant≥2) → 완료
  → MARGINAL / INSUFFICIENT → Level 1

Level 1: 동일 카테고리 내 확장 (예: naica → DIGITAL_PCR 전체)
  → SUFFICIENT → 기존 + 확장 결과 merge
  → INSUFFICIENT → Level 2

Level 2: 필터 없이 전체 KB 검색
  → 최종 결과 사용
  → INSUFFICIENT이면 "I Don't Know" 경로
```

#### 품질 평가 기준

```java
public record RetrievalQuality(QualityLevel level, double maxScore, int relevantResults) {
    enum QualityLevel { SUFFICIENT, MARGINAL, INSUFFICIENT }

    public static RetrievalQuality evaluate(List<EvidenceItem> evidences) {
        double max = evidences.stream().mapToDouble(EvidenceItem::score).max().orElse(0);
        int relevant = (int) evidences.stream().filter(e -> e.score() >= 0.40).count();

        if (max >= 0.60 && relevant >= 2) return SUFFICIENT;
        else if (max >= 0.40 && relevant >= 1) return MARGINAL;
        else return INSUFFICIENT;
    }
}
```

#### 임계값

| 임계값 | 값 | 근거 |
|--------|-----|------|
| MIN_RELEVANCE_THRESHOLD | 0.40 | 기존 `PerQuestionEvidence` 정의 |
| SUFFICIENT_MAX_SCORE | 0.60 | `AnalysisService.buildVerdict()` avg≥0.70 기준 참고 |
| SUFFICIENT_MIN_COUNT | 2 | 최소 2건 교차 검증 |

---

## 5. 변경 파일 목록

### Phase 1: SearchFilter 복수 제품군 (MVP Core)

| 파일 | 변경 내용 |
|------|----------|
| `SearchFilter.java` | `String productFamily` → `Set<String> productFamilies` |
| `ProductExtractorService.java` | `extractAll()` 메서드 추가, 모든 매칭 수집 |
| `QdrantVectorStore.java` | `match.value` → `match.any` |
| `MockVectorStore.java` | 단일 비교 → Set 포함 검사 |
| `PostgresKeywordSearchService.java` | `= ?` → `IN (?,?,?)` |
| `HybridSearchService.java` | 필터 전달 로직 업데이트 |
| `AnswerOrchestrationService.java` | `extract()` → `extractAll()`, `forProduct()` → `forProducts()` |

### Phase 2: 품질 기반 Fallback

| 파일 | 변경 내용 |
|------|----------|
| `RetrievalQuality.java` (신규) | 검색 결과 품질 평가 로직 |
| `ProductCategoryRegistry.java` (신규) | 제품군 → 카테고리 매핑, 확장 로직 |
| `AnswerOrchestrationService.java` | 3단계 fallback 로직 |

### Phase 3: 프론트엔드 UX

| 파일 | 변경 내용 |
|------|----------|
| `InquiryCreateForm.tsx` | 제품군 태그 자동 감지 + 수동 수정 UI |
| `InquiryAnswerTab.tsx` | 검색 사용 제품군 뱃지 표시 |
| `knowledge-base/page.tsx` | 제품군 드롭다운 + 자동완성 |
| `labels.ts` | 제품군 한글 레이블 추가 |
| `client.ts` | API 필드 확장 |

### DB 마이그레이션

**추가 마이그레이션 불필요**. 이미 인프라 준비 완료:
- `document_chunks.product_family` 컬럼 + 인덱스 (V26)
- `knowledge_documents.product_family` 컬럼 + 인덱스 (V14)
- Qdrant `productFamily` payload 인덱스 (자동 생성)

---

## 6. Agile 실행 계획

### 6.1 Epic 구조

```
Epic: 제품군 기반 지능형 검색 필터링
│
├── Story 1: 다중 제품군 추출 엔진 고도화
│   ├── Task 1.1: ProductExtractorService 다중 추출 지원
│   ├── Task 1.2: SearchFilter → Set<String> productFamilies
│   ├── Task 1.3: QdrantVectorStore 다중 productFamily 필터
│   ├── Task 1.4: PostgresKeywordSearchService 다중 필터
│   └── Task 1.5: MockVectorStore 다중 필터
│
├── Story 2: 제품군 마스터 데이터 관리
│   ├── Task 2.1: ProductFamily 마스터 목록 + 카테고리 계층
│   ├── Task 2.2: ProductExtractorService 패턴 → 마스터 연동
│   ├── Task 2.3: KB 업로드 API 제품군 검증
│   └── Task 2.4: 기존 KB 데이터 productFamily 정규화
│
├── Story 3: 문의 등록 시 제품군 태깅 UI
│   ├── Task 3.1: 제품군 태그 컴포넌트 개발
│   ├── Task 3.2: InquiryCreateForm 통합
│   ├── Task 3.3: Create Inquiry API 확장
│   └── Task 3.4: Inquiry 엔티티 productFamilies 저장
│
├── Story 4: Chunk 테이블 product_family 정합성
│   ├── Task 4.1: ChunkingService 청크 생성 시 productFamily 전파
│   ├── Task 4.2: VectorizingService 정합성 검증
│   └── Task 4.3: 기존 chunk 데이터 backfill
│
├── Story 5: 검색 결과 제품군 투명성 UI
│   ├── Task 5.1: 답변 탭 검색 제품군 표시
│   ├── Task 5.2: 근거 목록 제품군 뱃지
│   └── Task 5.3: 범위 확장 알림 UI
│
└── Story 6: 점진적 범위 확장 전략
    ├── Task 6.1: RetrievalQuality 품질 평가
    ├── Task 6.2: ProductCategoryRegistry 카테고리 확장
    └── Task 6.3: 3단계 fallback 로직
```

### 6.2 Sprint 계획

#### Sprint 1 (2주): 백엔드 검색 엔진 핵심 수정 — MVP Core

| Day | Task | SP | 담당 |
|-----|------|-----|------|
| D1-2 | Task 1.1: `ProductExtractorService` 다중 추출 | 3 | Backend |
| D2-3 | Task 1.2: `SearchFilter` 변경 + 하위 호환 | 3 | Backend |
| D3-4 | Task 1.3-1.5: VectorStore/KeywordSearch 다중 필터 | 5 | Backend |
| D4-5 | Task 4.1-4.2: ChunkingService productFamily 전파 | 3 | Backend |
| D5-6 | Task 2.1: ProductFamily 마스터 목록 | 2 | Backend |
| D7-8 | 통합 테스트 + 기존 테스트 수정 | 3 | Backend |
| D9-10 | 리뷰 + 버그 수정 + 데모 | 2 | Backend |
| **합계** | | **21 SP** | |

**완료 기준**: 다중 제품군 추출 + 필터링 백엔드 작동, 기존 단일 제품 하위 호환 유지

#### Sprint 2 (2주): 프론트엔드 UX + 데이터 정합성

| Day | Task | SP | 담당 |
|-----|------|-----|------|
| D1-3 | Task 3.1-3.2: 제품군 태그 UI + InquiryCreateForm 통합 | 5 | Frontend |
| D3-4 | Task 3.3-3.4: Create Inquiry API 확장 + 엔티티 변경 | 3 | Backend |
| D4-5 | Task 5.1-5.2: 답변 탭 제품군 뱃지 + 검색 범위 표시 | 3 | Frontend |
| D5-6 | Task 2.3-2.4: KB 업로드 검증 + 기존 데이터 정규화 | 3 | Backend |
| D6-7 | Task 4.3: 기존 chunk productFamily backfill | 2 | Backend |
| D8-9 | E2E 테스트 + 사용자 수용 테스트 | 3 | QA |
| D10 | 배포 + 모니터링 | 2 | DevOps |
| **합계** | | **21 SP** | |

**완료 기준**: 제품군 태그 UI, KB 데이터 정합성, 사용자 수용 테스트 통과

#### Sprint 3 (2주): 고도화

| Task | SP |
|------|-----|
| Task 6.1-6.3: 점진적 범위 확장 (3단계 fallback) | 5 |
| Task 5.3: 범위 확장 알림 UI | 2 |
| KB 제품군 드롭다운 + Autocomplete | 3 |
| 제품군별 정확도 메트릭 대시보드 | 5 |
| LLM 기반 제품군 추출 (정규식 보완) | 5 |
| **합계** | **20 SP** |

### 6.3 의존성 맵

```
Task 2.1 (마스터 목록) ──┬──▶ Task 1.1 (다중 추출)
                         ├──▶ Task 3.1 (태그 컴포넌트)
                         └──▶ Task 2.3 (KB 검증)

Task 1.1 (다중 추출) ────▶ Task 1.2 (SearchFilter)

Task 1.2 (SearchFilter) ─┬──▶ Task 1.3-1.5 (Store 필터)
                          └──▶ Task 4.1 (Chunk 전파)

Task 1.3-1.5 + 4.1 ─────▶ 통합 테스트
```

---

## 7. 요구사항 분류

### 7.1 Must-Have (MVP)

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| R1 | 다중 제품군 추출 (1~3개 동시) | P0 |
| R2 | SearchFilter 다중 제품 지원 (Set<String>) | P0 |
| R3 | 제품군 마스터 목록 도입 | P0 |
| R4 | Chunk 테이블 product_family 정합성 보장 | P0 |
| R5 | 문의 등록 시 제품군 태그 UI (자동 + 수동) | P1 |

### 7.2 Nice-to-Have

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| R6 | 점진적 범위 확장 (3단계 fallback) | P2 |
| R7 | 답변 탭 제품군 뱃지 + 검색 범위 표시 | P2 |
| R8 | KB 업로드 제품군 드롭다운 + Autocomplete | P2 |
| R9 | 제품군별 정확도 대시보드 | P3 |
| R10 | LLM 기반 제품군 추출 (정규식 보완) | P3 |

---

## 8. 리스크 및 완화 전략

### 8.1 기술 리스크

| 리스크 | 영향 | 확률 | 완화 전략 |
|--------|------|------|----------|
| 제품명 정규식 누락 | 높음 | 중간 | 마스터 목록 + 상담원 수동 태깅 + AI fallback (Sprint 3) |
| KB 데이터 productFamily 비정규화 | 높음 | 높음 | Sprint 2 정규화 마이그레이션 + 마스터 기반 유사도 매핑 |
| SearchFilter 하위 호환성 깨짐 | 중간 | 중간 | 기존 `forProduct(String)` 래퍼 유지 |
| 벡터 DB 재인덱싱 필요 | 중간 | 높음 | 기존 `indexAll()` 활용, 마이그레이션 후 일괄 실행 |

### 8.2 제품 리스크

| 리스크 | 완화 전략 |
|--------|----------|
| 과도한 필터링으로 Recall 하락 | 3단계 점진적 확장 + productFamily null인 문서는 항상 검색 포함 |
| 상담원 학습 비용 | 자동 추출 기본값, 태그 비어있어도 기존과 동일 작동 |
| 제품군 미분류 문서 누락 | null productFamily 문서는 항상 검색 대상 포함 |

---

## 9. A/B 테스트 설계

| 항목 | 대조군 (A) | 실험군 (B) |
|------|----------|----------|
| 검색 방식 | 기존 (전체 + 단일 필터) | 다중 필터 + 점진적 확장 |
| 표본 크기 | 최소 50건 | 최소 50건 |
| 기간 | 2주 | 2주 |
| 1차 지표 | 오답 혼입률 | 동일 |
| 2차 지표 | 상담원 수정 비율, 만족도 | 동일 |
| 통계 유의성 | p < 0.05 | - |

---

## 10. 테스트 전략

| 테스트 대상 | 관점 |
|------------|------|
| ProductExtractorService | 멀티 제품군 추출 (0, 1, 2, 3개 케이스) |
| SearchFilter | forProducts() 생성, hasProductFilter() 동작 |
| MockVectorStore | 복수 productFamily 필터 검색 |
| QdrantVectorStore | match.any 쿼리 정확성 |
| PostgresKeywordSearchService | IN 절 동작 |
| RetrievalQuality | 임계값 경계 케이스 (SUFFICIENT/MARGINAL/INSUFFICIENT) |
| AnswerOrchestrationService | 3단계 fallback 시나리오 |
| E2E | 단일/다중 제품 질문 → 답변에 해당 제품 정보만 포함 검증 |

---

## 11. 요약

### 즉시 실행 (Sprint 1)
1. `ProductExtractorService` → `List<ExtractedProduct>` 반환
2. `SearchFilter.productFamily` → `Set<String> productFamilies`
3. VectorStore/KeywordSearch 다중 OR 필터
4. 제품군 마스터 목록 도입

### 중기 실행 (Sprint 2)
1. 프론트엔드 제품군 태그 UI (자동 감지 + 수동 보정)
2. 기존 KB 데이터 productFamily 정규화
3. Chunk 테이블 productFamily 정합성

### 장기 실행 (Sprint 3+)
1. 3단계 점진적 범위 확장 (RetrievalQuality + ProductCategoryRegistry)
2. LLM 기반 제품군 추출
3. 제품군별 정확도 대시보드
