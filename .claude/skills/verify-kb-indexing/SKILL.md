---
name: verify-kb-indexing
description: KB 비동기 인덱싱 파이프라인 검증. 인덱싱 관련 코드 수정 후 사용.
---

## Purpose

1. **상태 머신 일관성** — UPLOADED → INDEXING → INDEXED/FAILED 상태 전이가 올바른지 검증
2. **@Async/@Transactional 패턴** — 비동기 워커가 별도 클래스에 분리되어 있고, afterCommit 콜백을 사용하는지 확인
3. **텍스트 추출 안전성** — DocumentTextExtractor를 사용하며, raw bytes 읽기를 하지 않는지 확인
4. **에러 핸들링** — 워커에서 예외를 잡아 FAILED 상태로 마킹하는지 검증
5. **HTTP 응답 코드** — 비동기 인덱싱 엔드포인트가 202 Accepted를 반환하는지 확인
6. **제품군 전파** — ChunkingService.chunkAndStore()에 productFamily 파라미터 전파 확인

## When to Run

- KB 인덱싱 관련 서비스/워커 코드 수정 후
- AsyncConfig 또는 스레드 풀 설정 변경 후
- KnowledgeBaseController 엔드포인트 수정 후
- DocumentTextExtractor 또는 ChunkingService 변경 후
- 상태 전이 로직 수정 후

## Related Files

| File | Purpose |
|------|---------|
| `backend/app-api/src/main/java/com/biorad/csrag/app/AsyncConfig.java` | @EnableAsync + 스레드 풀 설정 |
| `backend/app-api/src/main/java/com/biorad/csrag/application/knowledge/KnowledgeIndexingWorker.java` | 비동기 인덱싱 워커 |
| `backend/app-api/src/main/java/com/biorad/csrag/application/knowledge/KnowledgeBaseService.java` | KB CRUD + 인덱싱 오케스트레이션 |
| `backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/knowledge/KnowledgeDocumentJpaEntity.java` | KB 문서 엔티티 (상태 머신) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/KnowledgeBaseController.java` | REST 엔드포인트 |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/document/DocumentTextExtractor.java` | PDF/DOCX 텍스트 추출 |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/chunk/ChunkingService.java` | 청킹 서비스 |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/document/DocumentIndexingService.java` | 문의 문서 인덱싱 서비스 |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/vector/QdrantVectorStore.java` | Qdrant 벡터 스토어 (컬렉션 자동 생성 + 벡터 삭제) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/vector/MockVectorStore.java` | Mock 벡터 스토어 (동시성 안전 삭제) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/vector/VectorizingService.java` | 청크 벡터화 서비스 (KB 청크의 productFamily를 부모 문서에서 조회) |

## Workflow

### Step 1: @Async 클래스 분리 확인

**파일:** `KnowledgeIndexingWorker.java`

**검사:** @Async 메서드가 별도 @Component 클래스에 있는지 확인. Spring AOP 프록시 제약으로 같은 클래스 내 @Async 호출은 동작하지 않음.

```bash
grep -n "@Async\|@Component" backend/app-api/src/main/java/com/biorad/csrag/application/knowledge/KnowledgeIndexingWorker.java
```

**PASS:** @Component와 @Async가 같은 파일에 존재하고, KnowledgeBaseService와 다른 클래스
**FAIL:** @Async가 KnowledgeBaseService 내부에 존재

### Step 2: @Async executor 이름 일치 확인

**파일:** `AsyncConfig.java`, `KnowledgeIndexingWorker.java`

**검사:** @Bean 이름과 @Async 참조가 일치하는지 확인.

```bash
grep -n "kbIndexingExecutor" backend/app-api/src/main/java/com/biorad/csrag/app/AsyncConfig.java backend/app-api/src/main/java/com/biorad/csrag/application/knowledge/KnowledgeIndexingWorker.java
```

**PASS:** AsyncConfig의 @Bean(name = "kbIndexingExecutor")와 워커의 @Async("kbIndexingExecutor")가 일치
**FAIL:** 이름이 불일치하거나 @Async에 executor 이름이 없음

### Step 3: Propagation.REQUIRES_NEW 확인

**파일:** `KnowledgeIndexingWorker.java`

**검사:** 워커의 @Transactional이 REQUIRES_NEW propagation을 사용하는지 확인. 서비스 트랜잭션과 독립적이어야 함.

```bash
grep -n "REQUIRES_NEW\|Propagation" backend/app-api/src/main/java/com/biorad/csrag/application/knowledge/KnowledgeIndexingWorker.java
```

**PASS:** `@Transactional(propagation = Propagation.REQUIRES_NEW)` 존재
**FAIL:** 기본 propagation(REQUIRED) 사용 또는 @Transactional 누락

### Step 4: TransactionSynchronizationManager afterCommit 사용 확인

**파일:** `KnowledgeBaseService.java`

**검사:** 비동기 워커 호출이 afterCommit() 콜백 안에서 실행되는지 확인. 트랜잭션 커밋 전에 워커가 시작되면 데이터를 찾지 못함.

```bash
grep -n "afterCommit\|TransactionSynchronization" backend/app-api/src/main/java/com/biorad/csrag/application/knowledge/KnowledgeBaseService.java
```

**PASS:** `afterCommit()` 내에서 `indexingWorker.indexOneAsync()` 호출
**FAIL:** afterCommit 없이 직접 워커 호출

### Step 5: 상태 머신 유효 상태 확인

**파일:** `KnowledgeDocumentJpaEntity.java`

**검사:** 상태 전이 메서드가 올바른 상태값을 설정하는지 확인.

```bash
grep -n "mark\|status\s*=" backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/knowledge/KnowledgeDocumentJpaEntity.java
```

**PASS:** markIndexing() → "INDEXING", markIndexed() → "INDEXED", markFailed() → "FAILED" 존재
**FAIL:** 알 수 없는 상태값 사용 또는 markIndexing() 누락

### Step 6: 중복 인덱싱 방지 (409 Conflict)

**파일:** `KnowledgeBaseService.java`

**검사:** INDEXING 상태인 문서에 대한 재인덱싱 요청 시 409를 반환하는지 확인.

```bash
grep -n "INDEXING\|CONFLICT\|409" backend/app-api/src/main/java/com/biorad/csrag/application/knowledge/KnowledgeBaseService.java
```

**PASS:** status가 "INDEXING"일 때 ResponseStatusException(CONFLICT) throw
**FAIL:** 중복 인덱싱 가드 없음

### Step 7: HTTP 202 Accepted 반환 확인

**파일:** `KnowledgeBaseController.java`

**검사:** 인덱싱 엔드포인트가 202 Accepted를 반환하는지 확인.

```bash
grep -n "accepted\|202\|indexing" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/KnowledgeBaseController.java
```

**PASS:** `ResponseEntity.accepted()` 사용
**FAIL:** `ResponseEntity.ok()` 또는 200 반환

### Step 8: DocumentTextExtractor 사용 확인

**파일:** `KnowledgeIndexingWorker.java`, `DocumentIndexingService.java`

**검사:** PDF 텍스트 추출 시 DocumentTextExtractor를 사용하는지 확인 (raw bytes 읽기 금지).

```bash
grep -n "DocumentTextExtractor\|extractText\|extractByPage\|new String.*bytes" backend/app-api/src/main/java/com/biorad/csrag/application/knowledge/KnowledgeIndexingWorker.java backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/document/DocumentIndexingService.java
```

**PASS:** DocumentTextExtractor 의존성 주입 및 extractText()/extractByPage() 호출
**FAIL:** `new String(bytes, UTF-8)` 등 raw bytes 변환 존재

### Step 8b: 페이지별 추출 경로 확인 (fileName 포함)

**파일:** `KnowledgeIndexingWorker.java`, `DocumentIndexingService.java`

**검사:** OCR 불필요 경로에서 `extractByPage()` → `chunkAndStore(docId, pageTexts, sourceType, sourceId, fileName)` 5-arg 오버로드를 호출하는지 확인. RF-03에서 파일명 프리픽스를 위해 5번째 인자 `fileName`이 추가됨.

```bash
grep -n "extractByPage\|chunkAndStore.*pageTexts\|PageText\|getFileName" backend/app-api/src/main/java/com/biorad/csrag/application/knowledge/KnowledgeIndexingWorker.java backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/document/DocumentIndexingService.java
```

**PASS:** 비-OCR 경로에서 `extractByPage()` 호출 후 `chunkAndStore(docId, pageTexts, sourceType, sourceId, fileName)` 5-arg 사용, `doc.getFileName()` 전달
**FAIL:** `fileName` 인자 누락 (4-arg 오버로드 사용) 또는 null 전달

### Step 9: 워커 에러 핸들링 확인

**파일:** `KnowledgeIndexingWorker.java`

**검사:** 최상위 try-catch로 모든 예외를 잡아 markFailed()로 상태를 기록하는지 확인.

```bash
grep -n "catch\|markFailed\|lastError" backend/app-api/src/main/java/com/biorad/csrag/application/knowledge/KnowledgeIndexingWorker.java
```

**PASS:** catch(Exception)에서 markFailed(error) 호출 및 저장
**FAIL:** 예외 전파 또는 markFailed 누락

### Step 9b: 워커 문서 조회 시 null-safe 패턴 확인

**파일:** `KnowledgeIndexingWorker.java`

**검사:** @Async 워커에서 문서 조회 시 `ResponseStatusException` 대신 `orElse(null)` + null 체크 + return 패턴을 사용하는지 확인. @Async 메서드에서 ResponseStatusException은 Spring MVC 예외 핸들러에 도달하지 않음.

```bash
grep -n "ResponseStatusException\|orElse(null)\|doc == null" backend/app-api/src/main/java/com/biorad/csrag/application/knowledge/KnowledgeIndexingWorker.java
```

**PASS:** `findById(docId).orElse(null)` + `if (doc == null) return` 패턴, ResponseStatusException import 없음
**FAIL:** ResponseStatusException 사용 또는 orElseThrow() 사용

### Step 9c: 벡터 삭제 시 예외 전파 확인

**파일:** `QdrantVectorStore.java`, `KnowledgeBaseService.java`

**검사:** `QdrantVectorStore.deleteByDocumentId()`가 실패 시 RuntimeException을 throw하고, `KnowledgeBaseService.delete()`에서 이를 try-catch로 감싸 고스트 벡터 상황을 로깅하되 DB 삭제는 계속 진행하는지 확인.

```bash
grep -n "deleteByDocumentId\|RuntimeException\|ghost" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/vector/QdrantVectorStore.java backend/app-api/src/main/java/com/biorad/csrag/application/knowledge/KnowledgeBaseService.java
```

**PASS:** QdrantVectorStore에서 RuntimeException throw + KnowledgeBaseService에서 try-catch 감싸기 + error 로그
**FAIL:** QdrantVectorStore에서 예외 삼킴 또는 KnowledgeBaseService에서 벡터 삭제 실패 시 전체 롤백

### Step 9d: ensureCollection GET 우선 확인 패턴

**파일:** `QdrantVectorStore.java`

**검사:** `ensureCollection()`이 컬렉션 존재 여부를 GET 요청으로 먼저 확인하고, 없을 때만 PUT으로 생성하며, 생성 실패 시 `collectionReady`를 true로 설정하지 않는지 확인.

```bash
grep -n "GET\|exists\|collectionReady\|return;" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/vector/QdrantVectorStore.java
```

**PASS:** GET 확인 → 미존재 시 PUT 생성 → 실패 시 early return (collectionReady 미설정)
**FAIL:** GET 확인 없이 바로 PUT 또는 생성 실패에도 collectionReady = true

### Step 9e: MockVectorStore 동시성 안전 삭제

**파일:** `MockVectorStore.java`

**검사:** `deleteByDocumentId()`에서 ConcurrentHashMap의 entrySet을 직접 순회하며 삭제하지 않고, 삭제 대상 키를 먼저 수집한 후 별도로 삭제하는지 확인.

```bash
grep -n "toRemove\|stream\|forEach.*remove\|removeIf" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/vector/MockVectorStore.java
```

**PASS:** 키를 List로 수집 후 별도 삭제 또는 removeIf 사용
**FAIL:** entrySet 순회 중 직접 remove 호출 (ConcurrentModificationException 위험)

### Step 10: 워커 벡터 사전 삭제 확인 (유령 벡터 방지)

**파일:** `KnowledgeIndexingWorker.java`

**검사:** 재인덱싱 시 유령 벡터 방지를 위해 `vectorStore.deleteByDocumentId(docId)`를 청킹 전에 호출하는지 확인.

```bash
grep -n "deleteByDocumentId\|vectorStore" backend/app-api/src/main/java/com/biorad/csrag/application/knowledge/KnowledgeIndexingWorker.java
```

**PASS:** `vectorStore.deleteByDocumentId(docId)` 호출이 텍스트 추출/청킹 전에 존재
**FAIL:** 벡터 삭제 없이 바로 청킹 시작 (유령 벡터 위험)

### Step 11: ChunkingService 콘텐츠 기반 페이지 매핑 확인

**파일:** `ChunkingService.java`

**검사:** `resolvePageRange`가 오프셋 기반이 아닌 콘텐츠 매칭 방식(`findMatchingPage`)을 사용하는지 확인. 문장 분리/재결합 시 오프셋 드리프트 문제를 해결하기 위함.

```bash
grep -n "findMatchingPage\|normalizedPages\|resolvePageRange" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/chunk/ChunkingService.java
```

**PASS:** `findMatchingPage` 메서드 존재, `normalizedPages` 사전 계산, `resolvePageRange`가 문자열 콘텐츠를 받음
**FAIL:** 오프셋 기반 `resolvePageRange(int chunkStart, int chunkEnd, ...)` 사용

### Step 14: ChunkingService MAX_PAGE_SPAN 제한 확인

**파일:** `ChunkingService.java`

**검사:** `resolvePageRange()`에서 `MAX_PAGE_SPAN` 상수를 사용하여 chunk가 비정상적으로 넓은 페이지 범위에 걸리는 것을 방지하는지 확인. 중복 텍스트가 있는 PDF에서 오매칭 방지.

```bash
grep -n "MAX_PAGE_SPAN\|pageEnd - pageStart" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/chunk/ChunkingService.java
```

**PASS:** `MAX_PAGE_SPAN = 3` 상수 존재, `(pageEnd - pageStart) >= MAX_PAGE_SPAN` 검증 후 `pageEnd = pageStart` 보정
**FAIL:** MAX_PAGE_SPAN 제한 없음 또는 과도한 span 허용

### Step 15: ChunkingService pageEnd 역전 검증 확인

**파일:** `ChunkingService.java`

**검사:** `resolvePageRange()`에서 `pageEnd < pageStart`인 경우 `pageEnd = pageStart`로 보정하는지 확인. 중복 텍스트로 인해 끝 페이지가 시작 페이지보다 앞으로 매칭되는 오류 방지.

```bash
grep -n "pageEnd < pageStart\|searchFromIndex" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/chunk/ChunkingService.java
```

**PASS:** `pageEnd < pageStart` → `pageEnd = pageStart` 보정 + `searchFromIndex`로 pageEnd 검색을 pageStart 인덱스부터 시작
**FAIL:** 역전 검증 없음 또는 searchFromIndex 미사용

### Step 12: QdrantVectorStore deleteByDocumentId 컬렉션 미존재 안전 처리

**파일:** `QdrantVectorStore.java`

**검사:** `deleteByDocumentId()`가 컬렉션이 존재하지 않을 때 graceful하게 처리하는지 확인 (재인덱싱 첫 실행 시 발생 가능).

```bash
grep -n "doesn't exist\|collection_not_found\|deleteByDocumentId" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/vector/QdrantVectorStore.java
```

**PASS:** `msg.contains("doesn't exist")` 시 로그만 남기고 skip, 그 외 예외는 RuntimeException throw
**FAIL:** 컬렉션 미존재 시 무조건 예외 전파

### Step 13: 스레드 풀 설정 검증

**파일:** `AsyncConfig.java`

**검사:** 스레드 풀 설정이 적절한지 확인.

```bash
grep -n "corePoolSize\|maxPoolSize\|queueCapacity\|threadNamePrefix\|waitForTasks" backend/app-api/src/main/java/com/biorad/csrag/app/AsyncConfig.java
```

**PASS:** core=2, max=4, queue=50, prefix="kb-indexing-", waitForTasks=true
**FAIL:** 설정값 누락 또는 graceful shutdown 미설정

### Step 16: VectorizingService KB productFamily 부모 조회 확인

**파일:** `VectorizingService.java`

**검사:** KB 청크(`sourceType = "KNOWLEDGE_BASE"`)의 `productFamily`를 청크 자체에서 읽지 않고, `KnowledgeDocumentJpaRepository.findById(documentId)`를 통해 부모 문서에서 조회하는지 확인. ChunkingService가 청크에 productFamily를 설정하지 않으므로 청크에서 읽으면 항상 null이 됨.

```bash
grep -n "kbDocRepository\|KnowledgeDocumentJpaRepository\|KNOWLEDGE_BASE.*resolvedProductFamily\|getProductFamily" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/vector/VectorizingService.java
```

**PASS:** `kbDocRepository.findById(documentId).map(KnowledgeDocumentJpaEntity::getProductFamily)` 호출 존재, KNOWLEDGE_BASE 타입 체크 후 부모 문서에서 productFamily 조회
**FAIL:** `chunk.getProductFamily()` 직접 호출 (항상 null 반환) 또는 productFamily 조회 로직 없음

## Output Format

| # | 검사 항목 | 결과 | 상세 |
|---|----------|------|------|
| 1 | @Async 클래스 분리 | PASS/FAIL | 워커 클래스 위치 |
| 2 | Executor 이름 일치 | PASS/FAIL | Bean name vs @Async ref |
| 3 | REQUIRES_NEW propagation | PASS/FAIL | 트랜잭션 격리 |
| 4 | afterCommit 콜백 | PASS/FAIL | TSM 사용 여부 |
| 5 | 상태 머신 유효성 | PASS/FAIL | 상태 전이 메서드 |
| 6 | 중복 인덱싱 방지 | PASS/FAIL | 409 Conflict 가드 |
| 7 | HTTP 202 반환 | PASS/FAIL | 비동기 엔드포인트 응답 |
| 8 | TextExtractor 사용 | PASS/FAIL | PDF 추출 방식 |
| 9 | 워커 에러 핸들링 | PASS/FAIL | catch + markFailed |
| 9b | 워커 null-safe 패턴 | PASS/FAIL | orElse(null) + return |
| 9c | 벡터 삭제 예외 전파 | PASS/FAIL | RuntimeException + try-catch |
| 9d | ensureCollection GET 우선 | PASS/FAIL | GET → PUT → early return |
| 9e | MockVectorStore 동시성 | PASS/FAIL | 키 수집 후 삭제 |
| 10 | 워커 벡터 사전 삭제 | PASS/FAIL | deleteByDocumentId 호출 위치 |
| 11 | 콘텐츠 기반 페이지 매핑 | PASS/FAIL | findMatchingPage 존재 |
| 12 | 컬렉션 미존재 안전 처리 | PASS/FAIL | doesn't exist 처리 |
| 13 | 스레드 풀 설정 | PASS/FAIL | 설정값 적절성 |
| 14 | MAX_PAGE_SPAN 제한 | PASS/FAIL | 과도한 span 허용 |
| 15 | pageEnd 역전/span 검증 | PASS/FAIL | 역전 보정 + searchFromIndex |
| 16 | KB productFamily 부모 조회 | PASS/FAIL | VectorizingService → kbDocRepository |
| 17 | 파일명 프리픽스 (RF-03) | PASS/FAIL | applyFileNamePrefix + 오프셋 드리프트 방지 |
| 18 | 헤딩 인식 청킹 (RF-05) | PASS/FAIL | HEADING_PATTERN + isHeading() |
| 19 | 오버랩 300자 (RF-02) | PASS/FAIL | OVERLAP_CHARS = 300 |
| 20 | productFamily 파라미터 전파 | PASS/FAIL | chunkAndStore 오버로드 + setProductFamily |

### Step 17: ChunkingService 파일명 프리픽스 확인 (RF-03)

**파일:** `ChunkingService.java`

**검사:** `applyFileNamePrefix()` 헬퍼가 존재하고, 청크 콘텐츠 앞에 `[fileName] ` 프리픽스를 추가하는지 확인. 오프셋 계산에는 `rawContent`(프리픽스 미포함)를 사용하고, 저장 시에만 프리픽스를 적용하는지 확인 (globalOffset 드리프트 방지).

```bash
grep -n "applyFileNamePrefix\|rawContent\|globalOffset" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/chunk/ChunkingService.java
```

**PASS:** `applyFileNamePrefix(rawContent, fileName)` 존재, 오프셋은 `rawContent.length()`로 계산, `globalOffset`에 프리픽스 길이 미포함
**FAIL:** 프리픽스 적용 후 오프셋 계산 (드리프트 발생) 또는 applyFileNamePrefix 메서드 없음

### Step 18: ChunkingService 헤딩 인식 청킹 확인 (RF-05)

**파일:** `ChunkingService.java`

**검사:** `HEADING_PATTERN` 정규식과 `isHeading()` 메서드가 존재하고, 청크 병합 루프에서 제목 감지 시 새 청크를 시작하는지 확인.

```bash
grep -n "HEADING_PATTERN\|isHeading\|heading" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/chunk/ChunkingService.java
```

**PASS:** `HEADING_PATTERN` (Markdown heading, 번호 리스트, 대문자 제목) + `isHeading()` + 병합 루프에서 `isHeading(nextSentence)` 체크 후 break
**FAIL:** 헤딩 인식 없이 문장을 무조건 병합

### Step 19: ChunkingService 오버랩 300자 확인 (RF-02)

**파일:** `ChunkingService.java`

**검사:** `OVERLAP_CHARS` 상수가 300인지 확인. 100에서 300으로 증가하여 인접 청크 간 문맥 연속성 강화.

```bash
grep -n "OVERLAP_CHARS" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/chunk/ChunkingService.java
```

**PASS:** `OVERLAP_CHARS = 300`
**FAIL:** `OVERLAP_CHARS = 100` 또는 다른 값

### Step 20: ChunkingService productFamily 파라미터 전파 확인

**파일:** `ChunkingService.java`

**검사:** `chunkAndStore()` 오버로드에 `String productFamily` 파라미터가 존재하고, chunk 엔티티에 `setProductFamily(productFamily)` 호출이 있는지 확인.

```bash
grep -n "productFamily\|setProductFamily" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/chunk/ChunkingService.java
```

**PASS:** `chunkAndStore(..., String productFamily)` 오버로드 + `chunk.setProductFamily(productFamily)` 호출
**FAIL:** productFamily 파라미터 없음 또는 setProductFamily 미호출

## Exceptions

다음은 **위반이 아닙니다**:

1. **테스트 코드에서의 직접 호출** — 테스트에서 워커 메서드를 동기적으로 직접 호출하는 것은 정상 (afterCommit 불필요)
2. **Mock 서비스의 상태 단순화** — 테스트용 mock에서 UPLOADED → INDEXED 직접 전이는 허용
3. **개발 프로파일의 동기 실행** — `@Profile("test")` 등에서 동기 실행은 허용
