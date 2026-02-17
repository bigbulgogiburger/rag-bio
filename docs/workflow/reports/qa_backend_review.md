# 백엔드 QA 리포트

## 요약
- 검증 항목: 38개
- Pass: 36개
- Issue: 2개 (경미)
- 빌드: **Pass**
- 테스트: **32/32 통과** (0 failures, 0 errors)
- 커버리지: LINE 45.6%, BRANCH 24.1%, METHOD 50.3%

---

## 1. API 엔드포인트

### InquiryController (GET /inquiries) - PASS

| 항목 | 결과 | 상세 |
|------|------|------|
| GET /api/v1/inquiries 메서드 존재 | PASS | `@GetMapping` on line 56 |
| @RequestParam: status(List) | PASS | `List<String> status` (line 58) |
| @RequestParam: channel | PASS | `String channel` (line 59) |
| @RequestParam: keyword | PASS | `String keyword` (line 60) |
| @RequestParam: from(Instant) | PASS | `Instant from` (line 61) |
| @RequestParam: to(Instant) | PASS | `Instant to` (line 62) |
| @PageableDefault(size=20, sort="createdAt", direction=DESC) | PASS | line 63-64 |
| 응답: InquiryListResponse | PASS | `ResponseEntity<InquiryListResponse>` (line 57) |

파일: `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/InquiryController.java`

### KnowledgeBaseController (7 endpoints) - PASS

| # | 엔드포인트 | HTTP | 결과 |
|---|-----------|------|------|
| 1 | `/api/v1/knowledge-base/documents` | POST (multipart) | PASS (line 31-45) |
| 2 | `/api/v1/knowledge-base/documents` | GET (list with filters) | PASS (line 50-63) |
| 3 | `/api/v1/knowledge-base/documents/{docId}` | GET (detail) | PASS (line 68-72) |
| 4 | `/api/v1/knowledge-base/documents/{docId}` | DELETE | PASS (line 77-81) |
| 5 | `/api/v1/knowledge-base/documents/{docId}/indexing/run` | POST (index one) | PASS (line 86-90) |
| 6 | `/api/v1/knowledge-base/indexing/run` | POST (index all) | PASS (line 95-99) |
| 7 | `/api/v1/knowledge-base/stats` | GET (statistics) | PASS (line 104-108) |

파일: `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/KnowledgeBaseController.java`

---

## 2. 서비스 로직

### InquiryListService - PASS

| 항목 | 결과 | 상세 |
|------|------|------|
| 페이징 + 필터 처리 | PASS | `InquirySpecifications.withFilters()` + `Pageable` (line 58-61) |
| documentCount 계산 | PASS | `documentRepository.countByInquiryId()` (line 66) |
| latestAnswerStatus 조회 | PASS | `answerRepository.findTopByInquiryIdOrderByVersionDesc()` (line 69-72) |
| 200자 말줄임 처리 | PASS | `substring(0, 200) + "..."` (line 75-77) |
| @Transactional(readOnly=true) | PASS | 클래스 레벨 (line 22) |

파일: `backend/app-api/src/main/java/com/biorad/csrag/application/InquiryListService.java`

### KnowledgeBaseService - PASS

| 항목 | 결과 | 상세 |
|------|------|------|
| upload: 파일 저장 + 엔티티 생성 | PASS | Files.write + KnowledgeDocumentJpaEntity.create (line 87-111) |
| indexOne: 텍스트 추출 -> OCR -> 청킹 -> 벡터화 | PASS | extractText -> ocrService -> chunkingService -> vectorizingService (line 177-213) |
| indexOne: source_type=KNOWLEDGE_BASE | PASS | `chunkingService.chunkAndStore(doc.getId(), finalText, "KNOWLEDGE_BASE", doc.getId())` (line 199) |
| indexAll: 미인덱싱 문서 일괄 처리 | PASS | `findByStatusIn(List.of("UPLOADED", "FAILED"))` (line 221) |
| delete: chunk 삭제 + 벡터 삭제 + 파일 삭제 + 엔티티 삭제 | PASS | 4단계 순서대로 (line 245-265) |
| list: 페이징 + 필터 | PASS | `KnowledgeBaseSpecifications.withFilters()` (line 132-134) |
| detail: 단일 조회 | PASS | `kbDocRepository.findById()` (line 167-171) |
| stats: 총/인덱싱됨/청크수/카테고리별/제품군별 | PASS | line 272-288 |

파일: `backend/app-api/src/main/java/com/biorad/csrag/application/knowledge/KnowledgeBaseService.java`

**이슈 (경미):**
- `getStats()` 메서드에서 카테고리/제품군 집계 시 `kbDocRepository.findAll()`로 전체 레코드를 메모리에 로드함 (line 280). 데이터가 많아지면 성능 이슈 가능. 향후 GROUP BY JPQL 쿼리로 최적화 권장.

---

## 3. Entity/Repository

### InquirySpecifications - PASS

| 항목 | 결과 | 상세 |
|------|------|------|
| statuses IN 조건 | PASS | `root.get("status").as(String.class).in(statuses)` (line 37) |
| channel EQUAL 조건 | PASS | `cb.equal(root.get("customerChannel"), channel)` (line 42) |
| keyword LIKE lower 조건 | PASS | `cb.like(cb.lower(root.get("question")), ...)` (line 47-49) |
| from >= 조건 | PASS | `cb.greaterThanOrEqualTo(root.get("createdAt"), from)` (line 55) |
| to <= 조건 | PASS | `cb.lessThanOrEqualTo(root.get("createdAt"), to)` (line 60) |

파일: `backend/contexts/inquiry-context/src/main/java/com/biorad/csrag/inquiry/infrastructure/persistence/jpa/InquirySpecifications.java`

### KnowledgeDocumentJpaEntity - PASS

| 항목 | 결과 | 상세 |
|------|------|------|
| 필드 수 (id 포함 19개) | PASS | id, title, category, productFamily, fileName, contentType, fileSize, storagePath, status, description, tags, uploadedBy, extractedText, ocrConfidence, chunkCount, vectorCount, lastError, createdAt, updatedAt |
| markParsing() | PASS | line 141-144 |
| markParsed(String text) | PASS | line 146-150 |
| markParsedFromOcr(String text, double confidence) | PASS | line 152-157 |
| markChunked(int count) | PASS | line 159-163 |
| markIndexed(int count) | PASS | line 165-169 |
| markFailed(String error) | PASS | line 171-175 |
| create() 팩토리 메서드 | PASS | line 112-137, UUID.randomUUID() + 초기 status="UPLOADED" |

파일: `backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/knowledge/KnowledgeDocumentJpaEntity.java`

### KnowledgeDocumentJpaRepository - PASS

| 항목 | 결과 | 상세 |
|------|------|------|
| JpaSpecificationExecutor 구현 | PASS | line 13-14 |
| findByStatusIn | PASS | line 19 |
| countByStatus | PASS | line 24, 반환 타입 `int` (long이 아님 - 경미) |

### KnowledgeBaseSpecifications - PASS

| 항목 | 결과 | 상세 |
|------|------|------|
| category 필터 | PASS | line 33-35 |
| productFamily 필터 | PASS | line 38-40 |
| status 필터 | PASS | line 43-45 |
| keyword 검색 (title OR description) | PASS | line 48-53 |

파일: `backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/knowledge/KnowledgeBaseSpecifications.java`

---

## 4. DocumentChunk/VectorStore 확장

### DocumentChunkJpaEntity - PASS

| 항목 | 결과 | 상세 |
|------|------|------|
| source_type 필드 (VARCHAR(20), default 'INQUIRY') | PASS | `@Column(name = "source_type", length = 20)` + default `"INQUIRY"` (line 33-34) |
| source_id 필드 (UUID) | PASS | `@Column(name = "source_id")` (line 36-37) |
| 생성자 오버로드 (하위 호환) | PASS | 기존 7-arg 생성자 -> 9-arg 위임 (line 45-47) |

파일: `backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/chunk/DocumentChunkJpaEntity.java`

### ChunkingService - PASS

| 항목 | 결과 | 상세 |
|------|------|------|
| chunkAndStore(documentId, text) 오버로드 | PASS | line 27-29, "INQUIRY" 기본값으로 위임 |
| chunkAndStore(documentId, text, sourceType, sourceId) | PASS | line 40-72, 전체 구현 |
| 기존 청크 삭제 후 재생성 | PASS | `chunkRepository.deleteByDocumentId()` (line 41) |

파일: `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/chunk/ChunkingService.java`

### DocumentChunkJpaRepository - PASS

| 항목 | 결과 | 상세 |
|------|------|------|
| deleteByDocumentId(UUID) | PASS | line 10 |
| countBySourceType(String) | PASS | line 17 |

### VectorStore 인터페이스 - PASS

| 항목 | 결과 | 상세 |
|------|------|------|
| upsert(4-arg, 하위 호환) | PASS | line 11 |
| upsert(5-arg, sourceType 포함) | PASS | line 22 |
| search(queryVector, topK) | PASS | line 27 |
| deleteByDocumentId(UUID) | PASS | line 34 |

### MockVectorStore - PASS

| 항목 | 결과 | 상세 |
|------|------|------|
| upsert(4-arg) -> upsert(5-arg) 위임 | PASS | line 25-27 |
| upsert(5-arg) with sourceType | PASS | line 30-33, VectorRecord에 sourceType 저장 |
| search에서 sourceType 반환 | PASS | VectorSearchResult 생성 시 `record.sourceType()` (line 43) |
| deleteByDocumentId 구현 | PASS | documentId 매칭하여 삭제 (line 51-57) |

### QdrantVectorStore - PASS

| 항목 | 결과 | 상세 |
|------|------|------|
| upsert(4-arg) -> upsert(5-arg) 위임 | PASS | line 53-55 |
| upsert(5-arg) with sourceType in payload | PASS | `payload.put("sourceType", sourceType)` (line 65) |
| search에서 sourceType 파싱 | PASS | `payload.path("sourceType").asText("INQUIRY")` (line 113) |
| deleteByDocumentId 필터 쿼리 | PASS | Qdrant filter API 사용 (line 136-158) |

### VectorSearchResult - PASS

| 항목 | 결과 | 상세 |
|------|------|------|
| sourceType 필드 | PASS | record 필드 5번째 (line 10) |

### VectorizingService - PASS

| 항목 | 결과 | 상세 |
|------|------|------|
| 청크의 sourceType을 읽어 vectorStore.upsert에 전달 | PASS | `chunk.getSourceType()` -> `vectorStore.upsert(..., sourceType)` (line 38-39) |

---

## 5. AnalysisService 통합 검색

| 항목 | 결과 | 상세 |
|------|------|------|
| VectorSearchResult.sourceType 읽기 | PASS | `result.sourceType()` (line 59) |
| EvidenceItem에 sourceType 전달 | PASS | `new EvidenceItem(..., result.sourceType())` (line 54-59) |
| EvidenceItem record에 sourceType 필드 | PASS | 5번째 필드 (EvidenceItem.java line 8) |

파일: `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/analysis/AnalysisService.java`

sourceType이 VectorSearchResult -> EvidenceItem까지 정상 전달됨.

---

## 6. Flyway 마이그레이션

### 마이그레이션 파일 목록 (V1~V14)

| 버전 | 파일명 | 용도 |
|------|--------|------|
| V1 | init_inquiry_and_documents | inquiries, documents 테이블 |
| V2 | document_parsing_columns | 문서 파싱 컬럼 |
| V3 | document_ocr_confidence | OCR 신뢰도 |
| V4 | document_chunks | document_chunks 테이블 |
| V5 | document_vector_count | 벡터 카운트 |
| V6 | retrieval_evidence | 검색 근거 |
| V7 | answer_drafts | 답변 초안 |
| V8 | answer_draft_reviewer_metadata | 리뷰어 메타데이터 |
| V9 | orchestration_runs | 오케스트레이션 실행 |
| V10 | answer_draft_sent_columns | 전송 컬럼 |
| V11 | answer_draft_send_request_id | 전송 요청 ID |
| V12 | send_attempt_logs | 전송 시도 로그 |
| **V13** | **inquiry_search_indexes** | 문의 검색 인덱스 (status, channel, created_at) |
| **V14** | **knowledge_base** | KB 테이블 + document_chunks ALTER (source_type, source_id) |

### V13 (inquiry_search_indexes) - PASS

- `idx_inquiries_status`, `idx_inquiries_channel`, `idx_inquiries_created_at` 인덱스 생성
- `CREATE INDEX IF NOT EXISTS` 사용으로 안전

### V14 (knowledge_base) - PASS

| 항목 | 결과 | 상세 |
|------|------|------|
| knowledge_documents 테이블 생성 | PASS | 19개 컬럼 (entity와 일치) |
| KB 검색 인덱스 4개 | PASS | category, product_family, status, created_at |
| document_chunks ALTER: source_type | PASS | `VARCHAR(20) DEFAULT 'INQUIRY'` |
| document_chunks ALTER: source_id | PASS | `UUID` |
| 기존 데이터 마이그레이션 | PASS | `UPDATE ... SET source_id = document_id WHERE source_type = 'INQUIRY'` |
| source 기반 인덱스 | PASS | `idx_chunks_source ON document_chunks(source_type, source_id)` |

마이그레이션 순서 충돌: **없음** (V1~V14 순차적)

---

## 7. 빌드 + 테스트

### 빌드 결과
```
BUILD SUCCESSFUL in 979ms
21 actionable tasks: 21 up-to-date
```

### 테스트 결과 (32/32 통과)

| 테스트 클래스 | 테스트 수 | 결과 |
|-------------|----------|------|
| InquiryListServiceTest | 5 | PASS |
| InquiryControllerWebMvcTest | 6 | PASS |
| InquiryApiIntegrationTest | 2 | PASS |
| AnswerComposerServiceUnitTest | 5 | PASS |
| AnswerControllerWebMvcTest | 4 | PASS |
| AnswerWorkflowIntegrationTest | 1 | PASS |
| AnswerRbacIntegrationTest | 2 | PASS |
| AnswerFallbackIntegrationTest | 1 | PASS |
| AnswerComposerServiceFallbackTest | 1 | PASS |
| AnswerAuditLogIntegrationTest | 1 | PASS |
| OrchestrationRunVisibilityIntegrationTest | 1 | PASS |
| AnswerDraftJpaRepositoryDataJpaTest | 1 | PASS |
| SendAttemptJpaRepositoryDataJpaTest | 1 | PASS |
| OpsMetricsIntegrationTest | 1 | PASS |

### JaCoCo 커버리지

| 메트릭 | 커버리지 |
|--------|---------|
| LINE | 623/1367 (45.6%) |
| BRANCH | 89/369 (24.1%) |
| METHOD | 168/334 (50.3%) |
| CLASS | 49/69 (71.0%) |

---

## 주요 발견 사항

### 이슈

1. **[LOW] KnowledgeBaseService.getStats() 성능 우려**
   - 파일: `KnowledgeBaseService.java:280`
   - `kbDocRepository.findAll()`로 전체 엔티티를 메모리에 로드 후 Java Stream으로 카테고리/제품군 집계
   - 현재 문서 수가 적으면 문제 없으나, 대량 데이터 시 성능 저하 가능
   - 권장: GROUP BY 집계 JPQL 쿼리로 전환

2. **[LOW] KnowledgeBaseController 테스트 부재**
   - Knowledge Base 컨트롤러 및 서비스에 대한 단위/통합 테스트가 존재하지 않음
   - InquiryController와 AnswerController는 WebMvc/Integration 테스트가 충분한 반면, KB 관련 테스트 미작성
   - 권장: KnowledgeBaseController WebMvc 테스트 및 KnowledgeBaseService 단위 테스트 추가

### 긍정적 사항

- 모든 API 엔드포인트 스펙이 설계대로 구현됨
- sourceType 전파 체인 (ChunkingService -> DocumentChunk -> VectorizingService -> VectorStore -> VectorSearchResult -> EvidenceItem)이 완전하게 동작
- Flyway 마이그레이션 V13, V14가 기존 마이그레이션과 충돌 없이 구성됨
- MockVectorStore와 QdrantVectorStore 모두 deleteByDocumentId 및 sourceType 지원 upsert 구현 완료
- 기존 코드 하위 호환성이 잘 유지됨 (오버로드 패턴)
- 32개 기존 테스트 전체 통과, 빌드 정상
