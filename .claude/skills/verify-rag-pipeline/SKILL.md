---
name: verify-rag-pipeline
description: RAG 파이프라인 (답변 작성 + 분석 + 다운로드) 검증. 답변 작성/분석/다운로드 관련 코드 수정 후 사용.
---

## Purpose

1. **격식체 프롬프트 규칙** — OpenAiComposeStep 시스템 프롬프트에 8개 규칙 (격식체, 마크다운 금지, 번호 인용 금지, 인라인 인용 형식 등) 존재 확인
2. **DefaultComposeStep 톤 템플릿** — 3 tone × 3 verdict 조합이 격식체이고 자연어 인용 사용 확인
3. **Citation 형식 일관성** — `chunk=UUID score=0.xxx documentId=UUID fileName=... pageStart=... pageEnd=...` 형식 확인
4. **EvidenceItem 필드 완전성** — record 필드 (chunkId, documentId, score, excerpt, sourceType, fileName, pageStart, pageEnd, productFamily) 확인
5. **N+1 방지 배치 조인** — AnalysisService에서 findAllById 배치 조회 + sourceId fallback 조회 사용 확인
6. **다운로드 API 안전성** — DocumentDownloadController 엔드포인트 경로, Content-Disposition, 페이지 범위 검증 확인
7. **Verdict 임계값 정확성** — SUPPORTED ≥ 0.70, CONDITIONAL ≥ 0.45, REFUTED < 0.45 임계값 확인
8. **Evidence 전문 전달** — summarize()가 chunk 내용을 절단하지 않고 전문 전달하는지 확인
9. **LLM 프롬프트 중립성** — OpenAiComposeStep 프롬프트에 verdict/confidence/riskFlags가 노출되지 않는지 확인
10. **Guardrails 조건부 적용** — DefaultComposeStep guardrails가 SAFETY_CONCERN/REGULATORY_RISK에서만 작동하는지 확인
11. **질문 분해 패턴** — QuestionDecomposerService가 multi-question 패턴 (질문 N), N., #N)) 을 올바르게 파싱하는지 확인
12. **제품명 추출** — ProductExtractorService가 Bio-Rad 제품 패턴을 인식하고 SearchFilter에 반영하는지 확인
13. **per-question 증거 매핑** — AnswerOrchestrationService가 하위 질문별 증거를 독립 검색하고 매핑하는지 확인
14. **I Don't Know 경로** — 증거 부족 시 "확인 후 별도로 답변드리겠습니다" 생성 확인
15. **인덱싱 가드** — AnswerComposerService가 INDEXED 문서 없으면 답변 생성 차단하는지 확인
16. **하위 질문 완전성 검토** — SelfReviewStep.checkSubQuestionCompleteness()가 누락 답변 감지하는지 확인
17. **한국어 쿼리 정규화 (RF-01)** — PostgresKeywordSearchService.normalizeKorean()이 조사/어미를 제거하고 기술 용어를 보존하는지 확인
18. **벡터 검색 필터 변환 (RF-04)** — HybridSearchService.resolveForVectorSearch()가 inquiryId를 documentIds로 변환하는지 확인
19. **복수 제품 필터 검색 (PRD v6)** — SearchFilter.forProducts(), ProductFamilyRegistry.expand(), 3단계 retrieval fallback 확인
20. **멀티 제품 추출** — ProductExtractorService.extractAll() 멀티 추출 + SubQuestion.productFamilies 확인

## When to Run

- ComposeStep (OpenAi/Default) 프롬프트 또는 템플릿 수정 후
- AnswerComposerService citations 형식 변경 후
- EvidenceItem record 필드 추가/변경 후
- AnalysisService retrieve/verify 로직 수정 후
- DocumentDownloadController 엔드포인트 수정 후
- AnswerOrchestrationService 오케스트레이션 흐름 수정 후
- QuestionDecomposerService 질문 분해 패턴 수정 후
- ProductExtractorService 제품명 패턴 수정 후
- SearchFilter 필터 조건 변경 후
- SelfReviewStep 품질 검토 규칙 수정 후

## Related Files

| File | Purpose |
|------|---------|
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/OpenAiComposeStep.java` | AI 답변 생성 (시스템 프롬프트 + buildPrompt) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/DefaultComposeStep.java` | 폴백 답변 템플릿 (격식체) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/AnswerComposerService.java` | 답변 오케스트레이션 + citations 조립 + @Transactional |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/AnswerController.java` | REST 엔드포인트 (draft, review, approve, send, ai-review, ai-approve, auto-workflow, edit-draft) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/EditDraftRequest.java` | 답변 본문 수정 요청 DTO |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/agent/ReviewAgentService.java` | AI 리뷰 에이전트 (LLM 품질 검토) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/agent/ApprovalAgentService.java` | AI 승인 에이전트 (규칙 기반 4단 게이트) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/agent/ReviewResult.java` | AI 리뷰 결과 record |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/agent/ApprovalResult.java` | AI 승인 결과 record |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/agent/GateResult.java` | 게이트 결과 record |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/AnswerDraftResponse.java` | 답변 초안 응답 record (workflowRunCount 포함) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/AnswerHistoryDetailResponse.java` | 답변 이력 상세 응답 (AI 리뷰 이력 포함) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/analysis/EvidenceItem.java` | 근거 데이터 record |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/analysis/AnalysisService.java` | 근거 검색 + 판정 + 배치 조인 + 번역 + 하이브리드 검색 |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/analysis/AnalyzeResponse.java` | 분석 결과 record (translatedQuery 포함) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/search/HybridSearchService.java` | 벡터+키워드 RRF 결합 검색 |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/search/QueryTranslationService.java` | 질문 번역 인터페이스 (한→영) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/search/OpenAiQueryTranslationService.java` | OpenAI GPT 기반 번역 구현 |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/search/PostgresKeywordSearchService.java` | PostgreSQL tsvector 키워드 검색 |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/vector/QdrantVectorStore.java` | Qdrant 벡터 스토어 구현 (검색 + upsert + delete) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/vector/VectorStore.java` | 벡터 스토어 인터페이스 (search + upsert + deleteByDocumentId) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/document/DocumentDownloadController.java` | 문서 다운로드 + PDF 페이지 추출 |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/AnswerOrchestrationService.java` | 답변 오케스트레이션 (DECOMPOSE → per-Q RETRIEVE → VERIFY → COMPOSE) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/QuestionDecomposerService.java` | 규칙 기반 질문 분해 (multi-question → sub-questions) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/SubQuestion.java` | 하위 질문 record (index, text) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/DecomposedQuestion.java` | 질문 분해 결과 record (subQuestions, isMultiQuestion) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/PerQuestionEvidence.java` | 하위 질문별 증거 record (subQuestion, evidences, sufficient) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/DefaultRetrieveStep.java` | 기본 검색 (단일 + SearchFilter + per-question 오버로드) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/SelfReviewStep.java` | 규칙 기반 품질 검토 (중복, 일관성, 절차, 인용, 하위질문) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/search/SearchFilter.java` | 검색 필터 DTO (inquiryId, productFamily, documentIds, sourceTypes) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/search/ProductExtractorService.java` | 제품명 추출 (12 Bio-Rad 제품 패턴) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/search/KeywordSearchService.java` | 키워드 검색 인터페이스 (tsvector) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/search/ProductFamilyRegistry.java` | 10개 제품군 + 6개 카테고리 매핑 + expand() |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/search/ProductFamilyController.java` | GET /api/v1/product-families 엔드포인트 |
| `backend/app-api/src/test/java/com/biorad/csrag/interfaces/rest/search/NormalizeKoreanTest.java` | 한국어 정규화 단위 테스트 |
| `backend/app-api/src/test/java/com/biorad/csrag/interfaces/rest/search/ProductExtractorServiceTest.java` | 멀티 제품 추출 테스트 |
| `backend/app-api/src/test/java/com/biorad/csrag/interfaces/rest/search/SearchFilterTest.java` | 복수 제품 필터 테스트 |
| `backend/app-api/src/test/java/com/biorad/csrag/interfaces/rest/search/ProductFamilyRegistryTest.java` | 제품군 레지스트리 테스트 |

## Workflow

### Step 1: OpenAiComposeStep 시스템 프롬프트 8대 규칙 확인

**파일:** `OpenAiComposeStep.java`

**검사:** 시스템 프롬프트에 격식체, 마크다운 금지, 번호 인용 금지 등 핵심 규칙이 포함되어 있는지 확인.

```bash
grep -n "격식체\|마크다운.*금지\|번호 인용 금지\|이모지.*금지" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/OpenAiComposeStep.java
```

**PASS:** 격식체 존댓말, 마크다운 서식 금지, [1],[2] 번호 인용 금지, 이모지 금지, 인라인 인용 형식 `(파일명, p.XX)` 규칙 모두 존재
**FAIL:** 핵심 규칙 누락 또는 마크다운 허용 문구 존재

### Step 1a: OpenAiComposeStep 인라인 인용 형식 규칙 확인

**파일:** `OpenAiComposeStep.java`

**검사:** 시스템 프롬프트와 buildPrompt에 인라인 인용 형식 `(파일명, p.XX)` 또는 `(파일명, p.XX-YY)` 지시가 있는지 확인.

```bash
grep -n "파일명.*p\.\|인용.*괄호\|출처.*표기" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/OpenAiComposeStep.java
```

**PASS:** 인라인 인용 형식 지시 존재 (시스템 프롬프트 규칙 6 + buildPrompt 요구사항 7)
**FAIL:** 인라인 인용 형식 지시 없음

### Step 2: OpenAiComposeStep 프롬프트 섹션 형식 확인

**파일:** `OpenAiComposeStep.java`

**검사:** buildPrompt()에서 `[분석 결과]`, `[참고 자료]`, `[요구사항]` 평문 섹션을 사용하는지 확인. `##`, `**` 마크다운 미사용 확인.

```bash
grep -n "\[분석 결과\]\|\[참고 자료\]\|\[요구사항\]\|##\|\\*\\*" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/OpenAiComposeStep.java
```

**PASS:** `[분석 결과]`, `[참고 자료]`, `[요구사항]` 존재하고 `##`/`**` 마크다운 없음
**FAIL:** 마크다운 헤더(`##`) 또는 볼드(`**`) 사용

### Step 3: DefaultComposeStep 톤 템플릿 격식체 확인

**파일:** `DefaultComposeStep.java`

**검사:** 모든 톤 템플릿이 격식체 존댓말("드립니다", "바랍니다", "겠습니다")을 사용하는지 확인.

```bash
grep -n "드립니다\|바랍니다\|겠습니다\|주시기" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/DefaultComposeStep.java
```

**PASS:** 격식체 어미 다수 존재
**FAIL:** 반말 또는 비격식체 어미 사용

### Step 4: DefaultComposeStep 채널 포맷 확인

**파일:** `DefaultComposeStep.java`

**검사:** email 채널에 "안녕하세요"/"감사합니다", messenger 채널에 "[요약]" 태그 사용 확인.

```bash
grep -n "안녕하세요\|감사합니다\|\[요약\]" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/DefaultComposeStep.java
```

**PASS:** email에 인사/마무리, messenger에 [요약] 태그 존재
**FAIL:** 채널별 포맷 규칙 미준수

### Step 5: AnswerComposerService Citations 형식 확인

**파일:** `AnswerComposerService.java`

**검사:** citations 조립 시 `chunk=`, `score=`, `documentId=`, `fileName=`, `pageStart=`, `pageEnd=` 키를 포함하는지 확인.

```bash
grep -n "chunk=\|score=\|documentId=\|fileName=\|pageStart=\|pageEnd=" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/AnswerComposerService.java
```

**PASS:** 6개 키 모두 존재 (fileName, pageStart, pageEnd는 조건부)
**FAIL:** 필수 키 (chunk, score, documentId) 누락

### Step 6: EvidenceItem record 필드 확인

**파일:** `EvidenceItem.java`

**검사:** EvidenceItem record에 9개 필드가 모두 존재하는지 확인.

```bash
grep -n "chunkId\|documentId\|score\|excerpt\|sourceType\|fileName\|pageStart\|pageEnd\|productFamily" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/analysis/EvidenceItem.java
```

**PASS:** chunkId, documentId, score, excerpt, sourceType, fileName, pageStart, pageEnd, productFamily 모두 존재
**FAIL:** 필드 누락

### Step 7: AnalysisService N+1 방지 배치 조인 확인

**파일:** `AnalysisService.java`

**검사:** chunk/document 조회 시 `findAllById` 배치 쿼리를 사용하는지 확인. 반복문 내 개별 조회(N+1)가 없는지 확인.

```bash
grep -n "findAllById\|findById\|allLookupIds\|sourceId" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/analysis/AnalysisService.java
```

**PASS:** `findAllById` 사용 (chunkIds, docIds 배치) + sourceId fallback으로 allLookupIds 확장 조회
**FAIL:** 반복문 내 `findById` 개별 호출 존재 또는 sourceId fallback 누락

### Step 8: DocumentDownloadController 엔드포인트 확인

**파일:** `DocumentDownloadController.java`

**검사:** `/download`과 `/pages` 엔드포인트가 존재하고, Content-Disposition + UTF-8 인코딩을 사용하는지 확인.

```bash
grep -n "download\|pages\|Content-Disposition\|UTF-8" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/document/DocumentDownloadController.java
```

**PASS:** 두 엔드포인트 존재, Content-Disposition 헤더에 UTF-8 파일명 인코딩
**FAIL:** 엔드포인트 누락 또는 Content-Disposition 미설정

### Step 9: DocumentDownloadController 페이지 범위 검증

**파일:** `DocumentDownloadController.java`

**검사:** PDF 페이지 추출 시 from/to 범위 검증이 있는지 확인.

```bash
grep -n "from < 1\|to < from\|BAD_REQUEST\|NOT_FOUND" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/document/DocumentDownloadController.java
```

**PASS:** from >= 1, to >= from 검증 + 적절한 HTTP 상태 코드 (400, 404)
**FAIL:** 범위 검증 없이 PDF 페이지 추출 시도

### Step 9a: DocumentDownloadController download 파라미터 확인

**파일:** `DocumentDownloadController.java`

**검사:** `/pages` 엔드포인트에 `download` boolean 파라미터가 있고, `true`=attachment / `false`=inline으로 Content-Disposition이 설정되는지 확인.

```bash
grep -n "boolean download\|attachment\|inline" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/document/DocumentDownloadController.java
```

**PASS:** `@RequestParam(defaultValue = "false") boolean download` + disposition 분기
**FAIL:** download 파라미터 없음 또는 항상 attachment

### Step 10: DocumentDownloadController 이중 문서 조회

**파일:** `DocumentDownloadController.java`

**검사:** 문서 조회 시 Inquiry 문서와 KB 문서 양쪽을 모두 확인하는 이중 경로가 있는지 확인.

```bash
grep -n "documentRepository\|kbDocRepository\|resolveDocument" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/document/DocumentDownloadController.java
```

**PASS:** `documentRepository.findById()` → fallback `kbDocRepository.findById()` 이중 조회
**FAIL:** 한쪽 리포지토리만 조회

### Step 11: AnswerComposerService @Transactional 확인

**파일:** `AnswerComposerService.java`

**검사:** 클래스 레벨 @Transactional이 있고, 읽기 전용 메서드(history, latest)에 readOnly=true가 있는지 확인.

```bash
grep -n "@Transactional\|readOnly" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/AnswerComposerService.java
```

**PASS:** 클래스 레벨 @Transactional + history/latest에 @Transactional(readOnly = true)
**FAIL:** @Transactional 없음 또는 readOnly 누락

### Step 12: ApprovalAgentService 4단 게이트 확인

**파일:** `ApprovalAgentService.java`

**검사:** 4개 게이트(confidence, reviewScore, noCriticalIssues, noHighRiskFlags)가 존재하고, noHighRiskFlags가 SAFETY/REGULATORY/CONFLICTING만 차단하는지 확인.

```bash
grep -n "confidence\|reviewScore\|noCriticalIssues\|noHighRiskFlags\|SAFETY_CONCERN\|REGULATORY_RISK\|CONFLICTING_EVIDENCE" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/agent/ApprovalAgentService.java
```

**PASS:** 4개 게이트 + noHighRiskFlags가 3가지 위험 플래그만 차단
**FAIL:** 게이트 누락 또는 모든 riskFlags를 차단

### Step 13: EditDraft PATCH 엔드포인트 확인

**파일:** `AnswerController.java`

**검사:** `PATCH /{answerId}/edit-draft` 엔드포인트가 존재하고, SENT 상태 편집을 차단하는지 확인.

```bash
grep -n "edit-draft\|PatchMapping\|SENT.*edit\|Cannot edit" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/AnswerController.java
```

**PASS:** PATCH edit-draft 존재 + SENT 상태 차단 (409 Conflict)
**FAIL:** 엔드포인트 누락 또는 SENT 상태 편집 허용

### Step 14: QdrantVectorStore ensureCollection 안전성 확인

**파일:** `QdrantVectorStore.java`

**검사:** ensureCollection이 GET으로 존재 확인 후 PUT 생성하고, 실패 시 collectionReady를 true로 설정하지 않는지 확인.

```bash
grep -n "collectionReady\|GET.*collection\|return.*컬렉션" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/vector/QdrantVectorStore.java
```

**PASS:** GET 확인 → 조건부 PUT 생성 → 실패 시 early return
**FAIL:** 무조건 PUT 또는 실패해도 collectionReady=true 설정

### Step 15: QdrantVectorStore deleteByDocumentId 예외 전파 확인

**파일:** `QdrantVectorStore.java`

**검사:** deleteByDocumentId가 실패 시 예외를 전파하는지 확인 (조용한 무시 방지).

```bash
grep -n "throw.*RuntimeException\|deleteByDocumentId" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/vector/QdrantVectorStore.java
```

**PASS:** catch에서 RuntimeException throw (예외 전파)
**FAIL:** warn 로그만 남기고 예외 삼킴

### Step 16: AnalysisService 번역+하이브리드 검색 통합 확인

**파일:** `AnalysisService.java`

**검사:** `retrieve()`와 `analyze()` 모두 `queryTranslationService.translate()` 호출 후 번역된 텍스트로 `hybridSearchService.search()`를 사용하는지 확인. 번역 우회 방지.

```bash
grep -n "queryTranslationService\|hybridSearchService\|doRetrieve\|translate" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/analysis/AnalysisService.java
```

**PASS:** `retrieve()`에서 `translate()` 호출 + `doRetrieve()` 위임, `analyze()`에서도 `translate()` 호출
**FAIL:** `retrieve()`에서 번역 없이 직접 검색 (오케스트레이션 파이프라인 번역 우회)

### Step 17: AnalyzeResponse translatedQuery 필드 확인

**파일:** `AnalyzeResponse.java`

**검사:** `translatedQuery` 필드가 record에 존재하는지 확인.

```bash
grep -n "translatedQuery" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/analysis/AnalyzeResponse.java
```

**PASS:** `translatedQuery` 필드 존재 (nullable)
**FAIL:** 필드 누락

### Step 18: HybridSearchService RRF 결합 로직 확인

**파일:** `HybridSearchService.java`

**검사:** RRF(Reciprocal Rank Fusion) 결합이 벡터 + 키워드 양쪽 결과를 병합하고, `matchSource` 필드로 출처를 표시하는지 확인.

```bash
grep -n "rrf\|fusedScore\|matchSource\|VECTOR.*KEYWORD" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/search/HybridSearchService.java
```

**PASS:** RRF 점수 계산 + matchSource (VECTOR / KEYWORD / VECTOR+KEYWORD) 표시
**FAIL:** 단순 합산 또는 matchSource 미구분

### Step 19: QueryTranslationService ConditionalOnProperty 확인

**파일:** `OpenAiQueryTranslationService.java`, `MockQueryTranslationService.java`

**검사:** OpenAI 구현이 `openai.enabled=true` 조건, Mock 구현이 fallback으로 설정되는지 확인.

```bash
grep -n "ConditionalOnProperty\|Primary" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/search/OpenAiQueryTranslationService.java backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/search/MockQueryTranslationService.java
```

**PASS:** OpenAi에 `@ConditionalOnProperty(prefix = "openai", name = "enabled", havingValue = "true")` + `@Primary`
**FAIL:** 조건 어노테이션 누락

### Step 20: AnalysisService Verdict 임계값 확인

**파일:** `AnalysisService.java`

**검사:** SUPPORTED 임계값이 0.70, CONDITIONAL 임계값이 0.45인지 확인. 이전 과도한 임계값(0.82, 0.64)이 아닌지 검증.

```bash
grep -n "avg >= 0\.\|>= 0\." backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/analysis/AnalysisService.java
```

**PASS:** `avg >= 0.70` → SUPPORTED, `avg >= 0.45` → CONDITIONAL 존재
**FAIL:** 0.82 또는 0.64 등 과도한 임계값 사용

### Step 21: AnalysisService summarize() 비절단 확인

**파일:** `AnalysisService.java`

**검사:** `summarize()` 메서드가 chunk 내용을 절단(substring)하지 않고 공백 정규화만 수행하는지 확인. 160자 절단은 LLM에 불충분한 컨텍스트를 전달하여 hedging 답변 유발.

```bash
grep -n "summarize\|substring\|160\|Math.min.*length" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/analysis/AnalysisService.java
```

**PASS:** `summarize()`가 `replaceAll("\\s+", " ").trim()`만 수행, substring/절단 없음
**FAIL:** substring 호출 또는 문자수 제한 로직 존재

### Step 22: OpenAiComposeStep 프롬프트 verdict/confidence 미노출 확인

**파일:** `OpenAiComposeStep.java`

**검사:** `buildPrompt()`에서 verdict, confidence, riskFlags를 LLM에 전달하지 않는지 확인. 이 값들이 프롬프트에 포함되면 LLM이 자체 hedging하여 답변 품질 저하.

```bash
grep -n "verdict\|confidence\|riskFlags" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/OpenAiComposeStep.java
```

**PASS:** buildPrompt 내에 verdict/confidence/riskFlags 관련 문자열 없음
**FAIL:** 프롬프트에 verdict, confidence, riskFlags 중 하나라도 노출

### Step 23: DefaultComposeStep Guardrails 조건부 적용 확인

**파일:** `DefaultComposeStep.java`

**검사:** `applyGuardrails()`가 SAFETY_CONCERN 또는 REGULATORY_RISK riskFlag가 있을 때만 면책 문구를 삽입하는지 확인. confidence 임계값 기반 삽입은 불필요한 hedging 유발.

```bash
grep -n "SAFETY_CONCERN\|REGULATORY_RISK\|confidence.*0\.75\|applyGuardrails" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/DefaultComposeStep.java
```

**PASS:** SAFETY_CONCERN/REGULATORY_RISK 조건만 존재, `confidence < 0.75` 조건 없음
**FAIL:** `confidence < 0.75` 등 점수 기반 면책 문구 삽입 로직 존재

### Step 24: AnalysisService classifyQuestionPrior 미존재 확인

**파일:** `AnalysisService.java`

**검사:** 키워드 기반 verdict 선판정 메서드 `classifyQuestionPrior()`가 제거되었는지 확인. 고객 질문에 "risk", "condition" 같은 단어가 포함되면 내용과 무관하게 CONDITIONAL로 판정하는 오판 위험.

```bash
grep -n "classifyQuestionPrior\|QuestionPrior" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/analysis/AnalysisService.java
```

**PASS:** `classifyQuestionPrior` 문자열 없음 (메서드 제거됨)
**FAIL:** `classifyQuestionPrior` 메서드 또는 호출 존재

## Output Format

| # | 검사 항목 | 결과 | 상세 |
|---|----------|------|------|
| 1 | 시스템 프롬프트 8대 규칙 | PASS/FAIL | 누락 규칙 목록 |
| 2 | 프롬프트 평문 섹션 | PASS/FAIL | 마크다운 사용 여부 |
| 3 | 톤 템플릿 격식체 | PASS/FAIL | 비격식체 위치 |
| 4 | 채널 포맷 | PASS/FAIL | email/messenger 규칙 |
| 5 | Citations 형식 | PASS/FAIL | 누락 키 목록 |
| 6 | EvidenceItem 필드 | PASS/FAIL | 누락 필드 목록 |
| 7 | N+1 방지 배치 조인 | PASS/FAIL | 개별 쿼리 위치 |
| 8 | 다운로드 엔드포인트 | PASS/FAIL | 누락 항목 |
| 1a | 인라인 인용 형식 | PASS/FAIL | (파일명, p.XX) 지시 |
| 9 | 페이지 범위 검증 | PASS/FAIL | 검증 누락 항목 |
| 9a | download 파라미터 | PASS/FAIL | inline/attachment 분기 |
| 10 | 이중 문서 조회 | PASS/FAIL | 단일 경로 위치 |
| 11 | @Transactional 설정 | PASS/FAIL | readOnly 누락 |
| 12 | 4단 승인 게이트 | PASS/FAIL | 게이트 누락/과도한 차단 |
| 13 | EditDraft 엔드포인트 | PASS/FAIL | SENT 차단 여부 |
| 14 | ensureCollection 안전성 | PASS/FAIL | GET 확인 + early return |
| 15 | deleteByDocumentId 예외 전파 | PASS/FAIL | 예외 삼킴 여부 |
| 16 | 번역+하이브리드 검색 통합 | PASS/FAIL | retrieve()/analyze() 번역 호출 |
| 17 | AnalyzeResponse translatedQuery | PASS/FAIL | 필드 존재 |
| 18 | HybridSearch RRF 결합 | PASS/FAIL | matchSource 구분 |
| 19 | QueryTranslation ConditionalOnProperty | PASS/FAIL | 조건 어노테이션 |
| 20 | Verdict 임계값 (0.70/0.45) | PASS/FAIL | 과도한 임계값 사용 |
| 21 | summarize() 비절단 | PASS/FAIL | substring/절단 로직 |
| 22 | 프롬프트 verdict 미노출 | PASS/FAIL | verdict/confidence 포함 |
| 23 | Guardrails 조건부 적용 | PASS/FAIL | confidence 기반 삽입 |
| 24 | classifyQuestionPrior 미존재 | PASS/FAIL | 선판정 메서드 존재 |
| 25 | history-detail 엔드포인트 | PASS/FAIL | GET history-detail 존재 |
| 26 | autoWorkflow 재실행 제한 | PASS/FAIL | 5회 제한 + SENT 가드 |
| 27 | 리뷰 7개 카테고리 | PASS/FAIL | CITATION/HALLUCINATION 포함 |
| 28 | 리뷰 고객 질문 전달 | PASS/FAIL | 프롬프트에 질문 포함 |

### Step 25: history-detail 엔드포인트 확인

**파일:** `AnswerController.java`

**검사:** `GET /history-detail` 엔드포인트가 존재하고 `AnswerHistoryDetailResponse` 목록을 반환하는지 확인.

```bash
grep -n "history-detail\|AnswerHistoryDetailResponse" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/AnswerController.java
```

**PASS:** `@GetMapping("/history-detail")` + 반환 타입 `List<AnswerHistoryDetailResponse>` 존재
**FAIL:** 엔드포인트 누락 또는 반환 타입 불일치

### Step 26: autoWorkflow 재실행 제한 확인

**파일:** `AnswerController.java`

**검사:** `autoWorkflow()` 엔드포인트에 SENT 상태 거부(ConflictException)와 5회 실행 제한(ValidationException)이 있는지 확인.

```bash
grep -n "SENT\|workflowRunCount\|>= 5\|resetForReReview\|incrementWorkflowRunCount" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/AnswerController.java
```

**PASS:** SENT 상태 → 409 Conflict, workflowRunCount >= 5 → 400 Validation, 비-DRAFT 상태 시 resetForReReview() 호출
**FAIL:** SENT 가드 없음 또는 실행 횟수 제한 없음

### Step 27: ReviewAgentService 7개 리뷰 카테고리 확인

**파일:** `ReviewAgentService.java`

**검사:** 시스템 프롬프트에 ACCURACY, COMPLETENESS, CITATION, HALLUCINATION, TONE, RISK, FORMAT 7개 카테고리가 정의되어 있는지 확인.

```bash
grep -n "ACCURACY\|COMPLETENESS\|CITATION\|HALLUCINATION\|TONE\|RISK\|FORMAT" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/agent/ReviewAgentService.java
```

**PASS:** 7개 카테고리 모두 시스템 프롬프트에 존재 (특히 CITATION, HALLUCINATION 신규 추가)
**FAIL:** 5개 카테고리만 존재 (CITATION/HALLUCINATION 누락)

### Step 28: ReviewAgentService 고객 질문 전달 확인

**파일:** `ReviewAgentService.java`

**검사:** `buildPrompt()`에서 고객 질문 원문이 포함되고, `InquiryRepository`에서 질문을 조회하는지 확인.

```bash
grep -n "고객 질문\|customerQuestion\|InquiryRepository\|inquiryRepository" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/agent/ReviewAgentService.java
```

**PASS:** `inquiryRepository`를 통해 고객 질문 조회 + 프롬프트에 `## 고객 질문` 섹션 포함
**FAIL:** 고객 질문 전달 없이 답변 초안만으로 리뷰

### Step 29: QuestionDecomposerService 질문 분해 패턴 확인

**파일:** `QuestionDecomposerService.java`

**검사:** multi-question 분해 패턴 (질문 N), N), N., #N))이 정의되어 있고, 단일 질문은 분해하지 않는지 확인.

```bash
grep -n "질문.*N\|Pattern.*compile\|isMultiQuestion\|SubQuestion" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/QuestionDecomposerService.java
```

**PASS:** 4개 이상 분해 패턴 존재 + 단일 질문 시 isMultiQuestion=false 반환
**FAIL:** 분해 패턴 누락 또는 단일 질문도 분해

### Step 30: ProductExtractorService 제품명 패턴 확인

**파일:** `ProductExtractorService.java`

**검사:** Bio-Rad 주요 제품 패턴(naica, vericheck, QX, CFX, ddPCR, Bio-Plex 등)이 등록되어 있고, 가장 긴 매치를 선택하는지 확인.

```bash
grep -n "naica\|vericheck\|QX\|CFX\|ddPCR\|Bio-Plex\|longestMatch\|confidence\|extractAll" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/search/ProductExtractorService.java
```

**PASS:** 12개 이상 제품 패턴 + extractAll() 멀티 추출 (최대 3개, family 중복 제거) + longest match 선택 + confidence 점수 (0.9 exact, 0.6 partial)
**FAIL:** 주요 제품 패턴 누락 또는 첫 번째 매치만 반환

### Step 31: SearchFilter 기반 검색 확인

**파일:** `SearchFilter.java`, `DefaultRetrieveStep.java`

**검사:** SearchFilter에 팩토리 메서드(none, forInquiry, forProduct, forDocuments)가 있고, DefaultRetrieveStep에 SearchFilter 파라미터를 받는 오버로드가 있는지 확인.

```bash
grep -n "none()\|forInquiry\|forProduct\|forProducts\|forDocuments\|SearchFilter" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/search/SearchFilter.java backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/DefaultRetrieveStep.java
```

**PASS:** 5개 팩토리 메서드 (none, forInquiry, forProduct, forProducts, forDocuments) + DefaultRetrieveStep에 SearchFilter 오버로드 존재
**FAIL:** 팩토리 메서드 누락 또는 SearchFilter 오버로드 없음

### Step 32: per-question 증거 매핑 확인

**파일:** `AnswerOrchestrationService.java`, `DefaultRetrieveStep.java`

**검사:** 오케스트레이션에서 하위 질문별 독립 검색(executePerQuestion)을 수행하고, PerQuestionEvidence에 sufficient 플래그가 있는지 확인.

```bash
grep -n "executePerQuestion\|PerQuestionEvidence\|sufficient\|MIN_RELEVANCE" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/AnswerOrchestrationService.java backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/DefaultRetrieveStep.java backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/PerQuestionEvidence.java
```

**PASS:** executePerQuestion 호출 + PerQuestionEvidence.sufficient 필드 + MIN_RELEVANCE_THRESHOLD (0.40)
**FAIL:** 모든 하위 질문에 동일 증거 사용 또는 sufficient 판단 없음

### Step 33: I Don't Know 경로 확인

**파일:** `DefaultComposeStep.java`, `OpenAiComposeStep.java`

**검사:** 증거 부족 시 "확인 후 별도로 답변드리겠습니다" 문구가 생성되는지 확인. DefaultComposeStep과 OpenAiComposeStep 모두에 I Don't Know 경로가 있는지 확인.

```bash
grep -n "확인 후 별도\|확인 후.*답변\|I Don't Know\|증거.*부족\|sufficient.*false\|아니오" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/DefaultComposeStep.java backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/OpenAiComposeStep.java
```

**PASS:** 양쪽 ComposeStep에 증거 부족 시 "확인 후 별도로 답변드리겠습니다" 생성 로직 존재
**FAIL:** 증거 부족해도 답변을 생성하거나 빈 문자열 반환

### Step 34: 인덱싱 가드 확인

**파일:** `AnswerComposerService.java`

**검사:** 답변 생성 전 INDEXED 문서가 있는지 확인하는 가드 로직이 있는지 확인. 인덱싱되지 않은 문서로 답변 생성 시도를 차단.

```bash
grep -n "INDEXED\|NOT_INDEXED\|documentMeta\|indexing.*guard\|ValidationException" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/AnswerComposerService.java
```

**PASS:** INDEXED 문서 확인 → 없으면 ValidationException("NOT_INDEXED") 발생
**FAIL:** 인덱싱 상태 확인 없이 바로 오케스트레이션 호출

### Step 35: SelfReviewStep 하위 질문 완전성 확인

**파일:** `SelfReviewStep.java`

**검사:** `checkSubQuestionCompleteness()`가 질문의 하위 질문 수와 답변의 하위 답변 수를 비교하고, "확인 후" 패턴도 유효한 답변으로 카운트하는지 확인.

```bash
grep -n "checkSubQuestionCompleteness\|SUB_QUESTION_INCOMPLETE\|확인 후\|questionNumbers\|answerNumbers" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/SelfReviewStep.java
```

**PASS:** 하위 질문 수 감지 + 답변 수 비교 + "확인 후" 카운트 포함 + 부족 시 CRITICAL 이슈
**FAIL:** 하위 질문 완전성 검사 없음 또는 "확인 후" 미카운트

## Output Format (Updated)

| # | 검사 항목 | 결과 | 상세 |
|---|----------|------|------|
| ... | (기존 1~28 항목) | ... | ... |
| 29 | 질문 분해 패턴 | PASS/FAIL | 분해 패턴 존재 여부 |
| 30 | 제품명 추출 | PASS/FAIL | 12 패턴 + longest match |
| 31 | SearchFilter 기반 검색 | PASS/FAIL | 팩토리 메서드 + 오버로드 |
| 32 | per-question 증거 매핑 | PASS/FAIL | 독립 검색 + sufficient |
| 33 | I Don't Know 경로 | PASS/FAIL | 양쪽 ComposeStep |
| 34 | 인덱싱 가드 | PASS/FAIL | NOT_INDEXED 예외 |
| 35 | 하위 질문 완전성 | PASS/FAIL | SelfReview 검사 |
| 36 | normalizeKorean 전처리 (RF-01) | PASS/FAIL | 조사/어미 제거 + search() 호출 |
| 37 | resolveForVectorSearch (RF-04) | PASS/FAIL | inquiryId→documentIds 변환 |
| 38 | NormalizeKorean 테스트 (RF-01) | PASS/FAIL | 조사/어미/기술용어 케이스 |
| 39 | ProductFamilyRegistry 카테고리 매핑 | PASS/FAIL | 10 제품군 + 6 카테고리 + expand() |
| 40 | 3단계 retrieval fallback | PASS/FAIL | EXACT → CATEGORY_EXPANDED → UNFILTERED |
| 41 | SubQuestion productFamilies | PASS/FAIL | 필드 + enrichWithProductFamilies |
| 42 | 멀티 제품 필터 검색 | PASS/FAIL | Qdrant any + Postgres IN |

### Step 36: PostgresKeywordSearchService normalizeKorean 전처리 확인 (RF-01)

**파일:** `PostgresKeywordSearchService.java`

**검사:** `search()` 호출 전 `normalizeKorean()`으로 한국어 조사/어미를 제거하는지 확인. 영문/숫자/기술 용어(ddPCR, CFX96 등)는 보존되어야 함.

```bash
grep -n "normalizeKorean\|을/를\|조사.*제거\|JOSA_PATTERN\|ENDING_PATTERN" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/search/PostgresKeywordSearchService.java
```

**PASS:** `normalizeKorean()` 메서드 존재 + `search()` 내 쿼리 정규화 호출 + 조사/어미 패턴 정의
**FAIL:** `normalizeKorean()` 없음 또는 `search()`에서 호출하지 않음

### Step 37: HybridSearchService resolveForVectorSearch 필터 변환 확인 (RF-04)

**파일:** `HybridSearchService.java`

**검사:** `resolveForVectorSearch()`가 SearchFilter의 inquiryId를 벡터 검색용 documentIds로 변환하는지 확인. 벡터 스토어는 inquiryId SQL 서브쿼리를 지원하지 않으므로 사전 변환 필수.

```bash
grep -n "resolveForVectorSearch\|documentIds\|inquiryId\|chunkRepository\|findBySourceId" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/search/HybridSearchService.java
```

**PASS:** `resolveForVectorSearch()` 메서드 존재 + inquiryId → documentIds 변환 + 키워드 검색에 원본 필터, 벡터 검색에 변환된 필터 사용
**FAIL:** 벡터 검색에 inquiryId가 그대로 전달되거나 `resolveForVectorSearch()` 없음

### Step 38: NormalizeKorean 테스트 커버리지 확인 (RF-01)

**파일:** `NormalizeKoreanTest.java`

**검사:** 한국어 정규화 테스트가 존재하고 조사 제거, 어미 제거, 영문 보존 케이스를 검증하는지 확인.

```bash
grep -n "@Test\|normalizeKorean\|ddPCR\|QX700\|을\|합니다" backend/app-api/src/test/java/com/biorad/csrag/interfaces/rest/search/NormalizeKoreanTest.java
```

**PASS:** 조사 제거, 어미 제거, 기술 용어 보존 테스트 케이스 존재
**FAIL:** 테스트 파일 없음 또는 핵심 케이스 미커버

### Step 39: ProductFamilyRegistry 카테고리 매핑 확인

**파일:** `ProductFamilyRegistry.java`

**검사:** 10개 제품군이 6개 카테고리에 매핑되어 있고, expand()가 동일 카테고리 관련 제품을 반환하는지 확인.

```bash
grep -n "allFamilies\|categoryOf\|relatedFamilies\|expand\|ddPCR_Systems\|Real_Time_PCR" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/search/ProductFamilyRegistry.java
```

**PASS:** 10개 제품군 + 6개 카테고리 + allFamilies(), categoryOf(), relatedFamilies(), expand() 메서드 존재
**FAIL:** 제품군 수 부족 또는 expand() 메서드 누락

### Step 40: AnswerOrchestrationService 3단계 retrieval fallback 확인

**파일:** `AnswerOrchestrationService.java`

**검사:** 제품 필터 검색 → 카테고리 확장 검색 → 필터 없는 전체 검색 3단계 fallback이 구현되어 있는지 확인. RetrievalQuality enum (EXACT, CATEGORY_EXPANDED, UNFILTERED)이 존재하는지 확인.

```bash
grep -n "RetrievalQuality\|EXACT\|CATEGORY_EXPANDED\|UNFILTERED\|doRetrieveWithFilter\|expand" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/AnswerOrchestrationService.java
```

**PASS:** 3단계 fallback 로직 + RetrievalQuality enum + doRetrieveWithFilter 헬퍼 + ProductFamilyRegistry.expand() 호출
**FAIL:** 단일 검색만 수행하거나 fallback 없음

### Step 41: SubQuestion productFamilies 필드 확인

**파일:** `SubQuestion.java`, `QuestionDecomposerService.java`

**검사:** SubQuestion record에 `Set<String> productFamilies` 필드가 있고, QuestionDecomposerService에서 하위질문별 제품명을 추출하여 설정하는지 확인.

```bash
grep -n "productFamilies\|enrichWithProductFamilies\|extractAll" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/SubQuestion.java backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/QuestionDecomposerService.java
```

**PASS:** SubQuestion.productFamilies 필드 + QuestionDecomposerService.enrichWithProductFamilies() + ProductExtractorService.extractAll() 호출
**FAIL:** productFamilies 필드 없음 또는 enrichWithProductFamilies 미호출

### Step 42: 멀티 제품 필터 벡터/키워드 검색 확인

**파일:** `QdrantVectorStore.java`, `MockVectorStore.java`, `PostgresKeywordSearchService.java`, `MockKeywordSearchService.java`

**검사:** SearchFilter의 복수 productFamilies가 벡터 검색에서 `"match": {"any": [...]}` (Qdrant), 키워드 검색에서 `product_family IN (?,?,?)` 패턴으로 변환되는지 확인.

```bash
grep -n "any\|productFamilies\|IN (" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/vector/QdrantVectorStore.java backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/vector/MockVectorStore.java backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/search/PostgresKeywordSearchService.java backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/search/MockKeywordSearchService.java
```

**PASS:** Qdrant `"any": [...]` + Mock `anyMatch()` + Postgres `IN (?)` 동적 플레이스홀더 + MockKeyword 동일 패턴
**FAIL:** 단일 제품 `"match": {"value": ...}` 또는 `= ?` 단일 비교

## Exceptions

다음은 **위반이 아닙니다**:

1. **테스트 코드의 간략화** — 테스트에서 citations 형식을 단순화하거나 mock 값 사용은 허용
2. **OpenAI 비활성화 시 DefaultComposeStep 사용** — `OPENAI_ENABLED=false`에서 DefaultComposeStep이 primary로 동작하는 것은 정상
3. **비-PDF 파일의 페이지 정보 null** — Word/TXT 파일에서 pageStart/pageEnd가 null인 것은 정상 동작
4. **workflowRunCount 0** — 초기값 0은 워크플로우 미실행 상태를 나타내며 정상
5. **단일 질문의 비분해** — 하위 질문 패턴이 없는 단일 질문에 대해 QuestionDecomposerService가 isMultiQuestion=false를 반환하는 것은 정상
6. **SearchFilter.none()** — 필터 없는 검색은 기존 호환성을 위한 정상 동작
7. **RetrievalQuality.EXACT** — 제품 필터 첫 검색에서 결과가 충분한 경우 EXACT 반환은 정상 (fallback 불필요)
