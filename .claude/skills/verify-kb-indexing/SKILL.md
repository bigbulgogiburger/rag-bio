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

### Step 8b: 페이지별 추출 경로 확인

**파일:** `KnowledgeIndexingWorker.java`, `DocumentIndexingService.java`

**검사:** OCR 불필요 경로에서 `extractByPage()` → `chunkAndStore(docId, pageTexts, sourceType, sourceId)` 4-arg 오버로드를 호출하는지 확인.

```bash
grep -n "extractByPage\|chunkAndStore.*pageTexts\|PageText" backend/app-api/src/main/java/com/biorad/csrag/application/knowledge/KnowledgeIndexingWorker.java backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/document/DocumentIndexingService.java
```

**PASS:** 비-OCR 경로에서 `extractByPage()` 호출 후 `chunkAndStore(docId, pageTexts, ...)` 사용
**FAIL:** 비-OCR 경로에서도 `extractText()` + 2-arg `chunkAndStore()` 사용 (페이지 정보 누락)

### Step 9: 워커 에러 핸들링 확인

**파일:** `KnowledgeIndexingWorker.java`

**검사:** 최상위 try-catch로 모든 예외를 잡아 markFailed()로 상태를 기록하는지 확인.

```bash
grep -n "catch\|markFailed\|lastError" backend/app-api/src/main/java/com/biorad/csrag/application/knowledge/KnowledgeIndexingWorker.java
```

**PASS:** catch(Exception)에서 markFailed(error) 호출 및 저장
**FAIL:** 예외 전파 또는 markFailed 누락

### Step 10: 스레드 풀 설정 검증

**파일:** `AsyncConfig.java`

**검사:** 스레드 풀 설정이 적절한지 확인.

```bash
grep -n "corePoolSize\|maxPoolSize\|queueCapacity\|threadNamePrefix\|waitForTasks" backend/app-api/src/main/java/com/biorad/csrag/app/AsyncConfig.java
```

**PASS:** core=2, max=4, queue=50, prefix="kb-indexing-", waitForTasks=true
**FAIL:** 설정값 누락 또는 graceful shutdown 미설정

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
| 10 | 스레드 풀 설정 | PASS/FAIL | 설정값 적절성 |

## Exceptions

다음은 **위반이 아닙니다**:

1. **테스트 코드에서의 직접 호출** — 테스트에서 워커 메서드를 동기적으로 직접 호출하는 것은 정상 (afterCommit 불필요)
2. **Mock 서비스의 상태 단순화** — 테스트용 mock에서 UPLOADED → INDEXED 직접 전이는 허용
3. **개발 프로파일의 동기 실행** — `@Profile("test")` 등에서 동기 실행은 허용
