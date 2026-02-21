# PRD: RAG Pipeline Quantum Jump - 답변 품질 혁신

## 문서 정보
- **작성일**: 2026-02-21
- **우선순위**: P0 (라이브 클라이언트 컴플레인)
- **영향 범위**: Backend RAG Pipeline 전체 (Retrieve → Verify → Compose)

---

## 1. 문제 정의

### 1.1 라이브 클라이언트 컴플레인 요약

Bio-Rad CS 담당자가 실제 고객 문의에 대해 시스템을 테스트한 결과, **답변 품질이 실무 수준에 미달**하는 것으로 확인됨.

#### 케이스 A: naica 10x multiplex ddPCR mix 문의
```
질문:
  1) gDNA 사용 시 restriction enzyme 처리를 반드시 진행해야 하나요?
  2) 권장하는 primer&probe의 농도는 어떻게 되나요?

기대 답변:
  1) gDNA 사용 시 restriction enzyme을 mixture 제작 전에 처리 권장 (naica 10x multiplex ddpcr mix.pdf, p.6)
  2) final 0.125-1 uM, vortex 처리 필요 (동일 문서 p.6)

실제 답변: 구체적 수치/절차 없이 "근거 자료가 확인되었습니다" 수준의 일반적 템플릿
```

#### 케이스 B: QX700 시약 추천 문의
```
질문:
  1) QX700에서 gDNA + probe 기반 사용 가능한 시약 추천
  2) 10x multiplex naica 시약 사용 시 주의사항

문제: 제품-문서 매칭 실패, 다른 제품 자료에서 증거 추출
```

#### 케이스 C: Vericheck ddPCR EF capsid kit 문의
```
질문:
  1) dynamic range는? → 정답: 1×10^9 – 10^12 GC/ml or VP/ml (p.3)
  2) AAV serotype 적용 가능? → 정답: 2, 5, 8, 9 (p.1)
  3) crude sample 사용 가능? → 정답: 매뉴얼에 없음 → "확인 후 답변드리겠습니다"

V1 문제: 인덱싱 안 한 상태에서 다른 제품 매뉴얼로 답변 생성
V2 문제: 인덱싱 후에도 질문과 무관한 답변 전달
```

### 1.2 근본 원인 (Root Causes)

| ID | 근본 원인 | 코드 위치 | 심각도 |
|----|---------|---------|--------|
| **RC-1** | **Multi-Question 미분해**: 복수 질문을 단일 쿼리로 처리 | `AnswerOrchestrationService.run()` - 단일 `question` String | CRITICAL |
| **RC-2** | **Product-Aware 검색 부재**: 제품명 기반 필터링 없음 | `VectorStore.search()`, `KeywordSearchService.search()` - 필터 파라미터 없음 | CRITICAL |
| **RC-3** | **Template Compose의 한계**: 증거에서 구체적 사실 추출 불가 | `DefaultComposeStep.createDraftByTone()` - 12개 하드코딩 템플릿 | CRITICAL |
| **RC-4** | **"모르겠습니다" 경로 부재**: 증거 불충분해도 답변 생성 | `DefaultComposeStep` - CONDITIONAL verdict도 답변 템플릿 생성 | HIGH |
| **RC-5** | **인덱싱 가드 부재**: 미인덱싱 문서로 답변 생성 가능 | `AnswerComposerService.compose()` - 인덱싱 상태 미확인 | HIGH |

---

## 2. 솔루션 아키텍처

### 2.1 변경된 파이프라인 흐름

```
기존:
  question(전체) → Retrieve(단일) → Verify → Compose(템플릿) → SelfReview

변경:
  question(전체)
    → [NEW] IndexingGuard: 인덱싱 상태 확인
    → [NEW] QuestionDecomposer: 하위 질문 분리
    → [NEW] ProductExtractor: 제품명 추출
    → FOR EACH sub-question:
        → Retrieve(제품 필터 + 하위 질문별)
        → Verify(하위 질문별 증거 평가)
    → [ENHANCED] Compose(LLM 기반, 하위 질문별 답변 + "모름" 처리)
    → SelfReview(하위 질문 완성도 검증)
```

### 2.2 핵심 변경사항

#### CHANGE-1: Question Decomposition Service (신규)

**목적**: 복수 하위 질문을 개별 분리하여 각각 독립 검색

```java
// 새 서비스: QuestionDecomposerService
public record DecomposedQuestion(
    String originalQuestion,           // 원본 전체 질문
    List<SubQuestion> subQuestions      // 분리된 하위 질문들
)

public record SubQuestion(
    int index,                         // 1, 2, 3...
    String question,                   // "gDNA 사용 시 restriction enzyme 처리 필요한가?"
    String context                     // 원본에서의 맥락 (제품명 등)
)
```

**분리 전략** (2단계):
1. **규칙 기반 1차 분리**: `질문 ?1)`, `1.`, `첫째,` 등 번호 패턴 감지
2. **LLM 기반 2차 분리** (OpenAI 활성 시): 번호 패턴 없는 복합 질문도 분리

**예시**:
```
입력: "naica 10x multiplex ddpcr mix 시약 관련하여 문의드립니다.
       질문 1) gDNA 사용하여 실험 진행 시 restriction enzyme 처리를 반드시 진행하여야 하나요?
       질문 2) 권장하는 primer&probe의 농도는 어떻게 되나요?"

출력: [
  SubQuestion(1, "gDNA 사용 시 restriction enzyme 처리 필요 여부", "naica 10x multiplex ddpcr mix"),
  SubQuestion(2, "권장하는 primer&probe의 농도", "naica 10x multiplex ddpcr mix")
]
```

---

#### CHANGE-2: Product-Aware Retrieval (기존 수정)

**목적**: 질문에서 언급된 제품의 문서를 우선 검색

**2-A. ProductExtractor 신규 서비스**
```java
public record ExtractedProduct(
    String productName,         // "naica 10x multiplex ddpcr mix"
    String productFamily,       // "naica" 또는 "vericheck" 또는 "QX700"
    double confidence           // 0.0-1.0
)
```

- Bio-Rad 제품 카탈로그 패턴 매칭 (regex + 동의어 사전)
- 제품명 정규화: "naica multiplex" → "naica 10x multiplex ddpcr mix"

**2-B. VectorStore 필터 확장**
```java
// 기존
List<VectorSearchResult> search(List<Double> queryVector, int topK);

// 변경
List<VectorSearchResult> search(List<Double> queryVector, int topK, SearchFilter filter);

public record SearchFilter(
    UUID inquiryId,            // 해당 문의 문서 우선
    String productFamily,      // 제품 패밀리 필터
    Set<String> sourceTypes    // INQUIRY, KNOWLEDGE_BASE
)
```

**2-C. 2단계 검색 전략 (Scoped → Fallback)**
```
Step 1: Scoped Search (제품 필터 적용)
  → VectorStore.search(query, topK, filter={productFamily="naica"})
  → KeywordSearch.search(query, topK, filter={productFamily="naica"})
  → 결과가 minThreshold(3개) 이상이면 이 결과 사용

Step 2: Fallback Search (필터 완화)
  → 결과 부족 시 전체 검색으로 폴백
  → 제품 필터 없이 재검색
  → scoped 결과에 boost 가중치 적용 (x1.3)
```

**2-D. Inquiry-Scoped 검색**
- 해당 inquiry에 첨부된 문서의 청크를 최우선 검색
- `documentId` IN (inquiry의 documents) 필터 추가

---

#### CHANGE-3: LLM-Powered Per-Question Compose (기존 수정)

**목적**: 각 하위 질문에 대해 증거 기반 구체적 답변 생성

**기존 문제**: DefaultComposeStep은 verdict별 고정 템플릿 → 증거 내용 무시
**해결**: 모든 compose를 LLM 기반으로 전환 (DefaultComposeStep은 최후 폴백만)

**새로운 Compose 흐름**:
```
FOR EACH subQuestion:
  1. 해당 subQuestion의 evidence 목록 확인
  2. evidence가 충분하면 (score >= threshold):
     → LLM에게 evidence excerpts + question 전달
     → "이 증거를 바탕으로 구체적으로 답변하라" 프롬프트
     → 구체적 수치/절차/제품명 포함 답변 생성
  3. evidence가 불충분하면:
     → "확인 후 답변드리겠습니다" 메시지 생성 (I Don't Know)
  4. 인용 첨부: "(파일명, p.XX)" 형식

MERGE all sub-answers into single draft:
  → 번호별로 구조화: "1) ... 2) ... 3) ..."
  → 톤/채널 래핑 적용
```

**LLM Prompt 강화**:
```
당신은 Bio-Rad 기술지원 전문가입니다.

## 규칙
1. 반드시 제공된 근거 자료의 내용만 사용하여 답변하십시오
2. 근거에 없는 수치, 절차, 제품명을 추측하지 마십시오
3. 구체적인 수치(농도, 온도, 시간 등)가 근거에 있으면 반드시 포함하십시오
4. 근거가 불충분한 질문에는 "해당 내용은 제공된 자료에서 확인되지 않아, 확인 후 답변드리겠습니다"로 응답하십시오
5. 인용은 (파일명, p.XX) 형식으로 표기하십시오

## 질문
{subQuestion.question}

## 근거 자료
{evidences with fileName, pageStart, pageEnd, excerpt}
```

---

#### CHANGE-4: "I Don't Know" Pathway (신규)

**목적**: 증거 불충분 시 솔직하게 "모르겠습니다" 표현

**트리거 조건**:
1. 하위 질문의 evidence가 0개
2. 하위 질문의 최고 점수가 `minRelevanceThreshold` (0.40) 미만
3. LLM이 "evidence insufficient" 판단

**출력 문구**:
```
"해당 내용은 현재 등록된 자료에서 확인되지 않아, 확인 후 별도로 답변드리겠습니다."
```

**적용 위치**: ComposeStep에서 하위 질문별로 판단

---

#### CHANGE-5: Indexing Guard (신규)

**목적**: 문서 미인덱싱 상태에서 답변 생성 차단

**구현 위치**: `AnswerComposerService.compose()` 진입부

```java
// compose() 메서드 시작 시
List<DocumentMetadata> docs = documentRepository.findByInquiryId(inquiryId);
if (docs.isEmpty()) {
    throw new ValidationException("NO_DOCUMENTS", "문의에 첨부된 문서가 없습니다.");
}

boolean anyIndexed = docs.stream()
    .anyMatch(d -> d.getIndexingStatus() == IndexingStatus.COMPLETED);
if (!anyIndexed) {
    throw new ValidationException("NOT_INDEXED",
        "첨부 문서의 인덱싱이 완료되지 않았습니다. 인덱싱 완료 후 답변 생성을 진행해주세요.");
}
```

**프론트엔드 연동**: 에러 코드별 한국어 메시지 표시

---

#### CHANGE-6: Self-Review 강화 (기존 수정)

**목적**: 하위 질문 완성도 검증

**추가 검증 항목**:
```java
// SelfReviewStep에 추가
private void checkSubQuestionCompleteness(
    String draft,
    List<SubQuestion> subQuestions,
    List<QualityIssue> issues
) {
    for (SubQuestion sq : subQuestions) {
        // 각 하위 질문의 키워드가 답변에 포함되었는지 확인
        // 번호(1), 2), 3)) 구조가 질문 수와 일치하는지 확인
        // "확인 후 답변드리겠습니다"도 유효한 답변으로 인정
    }
}
```

---

## 3. 구현 모듈 설계

### 3.1 신규 파일

| 파일 | 위치 | 역할 |
|------|------|------|
| `QuestionDecomposerService.java` | `interfaces/rest/answer/orchestration/` | 질문 분해 서비스 |
| `ProductExtractorService.java` | `interfaces/rest/search/` | 제품명 추출 서비스 |
| `SearchFilter.java` | `interfaces/rest/search/` | 검색 필터 DTO |
| `SubQuestion.java` | `interfaces/rest/answer/orchestration/` | 하위 질문 DTO |
| `DecomposedQuestion.java` | `interfaces/rest/answer/orchestration/` | 분해 결과 DTO |
| `PerQuestionEvidence.java` | `interfaces/rest/answer/orchestration/` | 하위 질문별 증거 DTO |

### 3.2 수정 파일

| 파일 | 변경 내용 |
|------|---------|
| `AnswerOrchestrationService.java` | 질문 분해 → 하위 질문별 검색 → 통합 compose 흐름 |
| `AnswerComposerService.java` | 인덱싱 가드 추가 |
| `DefaultRetrieveStep.java` | 제품 필터 + inquiry 스코핑 적용 |
| `VectorStore.java` (인터페이스) | `search(vector, topK, filter)` 오버로드 추가 |
| `MockVectorStore.java` | 필터 지원 구현 |
| `QdrantVectorStore.java` | Qdrant payload filter 적용 |
| `HybridSearchService.java` | 필터 파라미터 전파 |
| `KeywordSearchService.java` (인터페이스) | 필터 파라미터 추가 |
| `PostgresKeywordSearchService.java` | WHERE 절 동적 필터 추가 |
| `MockKeywordSearchService.java` | 필터 지원 구현 |
| `DefaultComposeStep.java` | 하위 질문별 답변 + "모름" 처리 |
| `OpenAiComposeStep.java` | 하위 질문별 LLM 프롬프트 강화 |
| `SelfReviewStep.java` | 하위 질문 완성도 검증 추가 |
| `AnalysisService.java` | 제품 필터 전달 |
| `VectorizingService.java` | 청크 upsert 시 productFamily 메타데이터 추가 |
| `ChunkingService.java` | 청크에 productFamily 컬럼 추가 |

### 3.3 DB 마이그레이션

```sql
-- V25__add_product_family_to_chunks.sql
ALTER TABLE document_chunks ADD COLUMN product_family VARCHAR(100);
CREATE INDEX idx_chunks_product_family ON document_chunks(product_family);

-- V26__add_sub_questions_to_answer_draft.sql
ALTER TABLE answer_drafts ADD COLUMN sub_question_count INTEGER DEFAULT 1;
ALTER TABLE answer_drafts ADD COLUMN decomposed_questions TEXT;
```

---

## 4. 구현 우선순위

### Phase 1: 즉시 수정 (CRITICAL)

1. **CHANGE-5: Indexing Guard** — 미인덱싱 답변 생성 차단
2. **CHANGE-1: Question Decomposition** — 규칙 기반 질문 분리
3. **CHANGE-2: Product-Aware Retrieval** — 제품 필터 검색

### Phase 2: 품질 혁신 (HIGH)

4. **CHANGE-3: LLM Per-Question Compose** — 하위 질문별 LLM 답변
5. **CHANGE-4: "I Don't Know" Pathway** — 증거 부족 시 솔직한 응답

### Phase 3: 안전망 (MEDIUM)

6. **CHANGE-6: Self-Review 강화** — 하위 질문 완성도 검증

---

## 5. 성공 기준

| 메트릭 | 현재 | 목표 |
|--------|------|------|
| 하위 질문별 정확 응답률 | ~30% (추정) | ≥ 80% |
| 제품-문서 매칭 정확도 | ~50% (추정) | ≥ 90% |
| "모르겠습니다" 적절 사용률 | 0% | ≥ 90% (미답변 가능 질문 대상) |
| 미인덱싱 답변 생성 건수 | 발생 가능 | 0건 |
| 구체적 수치 포함 답변률 | ~20% (추정) | ≥ 70% |

---

## 6. 기술적 고려사항

### 6.1 하위 호환성
- `VectorStore.search(vector, topK)` 기존 시그니처 유지 (디폴트 필터=null)
- `DefaultComposeStep`은 폴백으로 유지
- API 응답 스키마 변경 없음 (답변 본문만 개선)

### 6.2 성능 영향
- 질문 분해: +100-200ms (규칙 기반) / +500ms (LLM)
- 하위 질문별 검색: 질문 수 × 검색 시간 (병렬화로 완화)
- LLM Compose: 기존과 유사 (단일 호출)

### 6.3 Mock 환경 지원
- 모든 신규 서비스에 Mock 구현체 제공
- `OPENAI_ENABLED=false`에서도 규칙 기반 분해/compose 동작

---

## 7. 팀 구성 및 작업 분배

| Agent | 담당 영역 | 주요 파일 |
|-------|---------|---------|
| **agent-1** | Question Decomposition + DTOs | QuestionDecomposerService, SubQuestion, DecomposedQuestion |
| **agent-2** | Product Extractor + Search Filter | ProductExtractorService, SearchFilter |
| **agent-3** | VectorStore 필터 확장 | VectorStore, MockVectorStore, QdrantVectorStore |
| **agent-4** | Keyword Search 필터 확장 | KeywordSearchService, PostgresKeywordSearchService, MockKeywordSearchService |
| **agent-5** | HybridSearch + Retrieve Step 통합 | HybridSearchService, DefaultRetrieveStep, AnalysisService |
| **agent-6** | Orchestration + Compose 혁신 | AnswerOrchestrationService, DefaultComposeStep, OpenAiComposeStep |
| **agent-7** | Indexing Guard + "I Don't Know" + SelfReview | AnswerComposerService, SelfReviewStep |
| **agent-8** | DB Migration + VectorizingService + ChunkingService | Flyway migration, 메타데이터 확장 |
