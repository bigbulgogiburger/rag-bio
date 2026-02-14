# 32. Sprint 8 실행백로그 — 지식 기반(Knowledge Base) 관리

> 상태: **완료 (핵심 기능 구현/빌드 검증 완료)**

## 목표

1. **문의와 독립적으로** Bio-Rad 기술 문서(매뉴얼·프로토콜·FAQ·스펙시트)를 등록·관리할 수 있는 Knowledge Base 모듈 구축
2. 기존 인덱싱 파이프라인(파싱 → 청킹 → 임베딩 → 벡터 저장)을 Knowledge Base 문서에도 재사용
3. RAG 분석 시 **문의 첨부 문서 + 지식 기반 문서**를 동시에 검색하는 **통합 검색** 구현
4. `/knowledge-base` 관리 UI 구현

---

## 운영 원칙

1. **기존 파이프라인 재사용 극대화**: 새로운 파싱/청킹/벡터화 코드를 만들지 않고 기존 서비스를 일반화(generalize)
2. **source 구분**: 벡터 검색 결과에 출처(문의 첨부 / 지식 기반)를 명확히 구분
3. **하위 호환**: 기존 API 응답 스키마에 `sourceType` 필드 추가 (기존 필드 변경 없음)

---

## BE-01. DB 마이그레이션 (P0)

### 작업 내용

**신규 파일:** `backend/app-api/src/main/resources/db/migration/V13__knowledge_base.sql`

```sql
-- Knowledge Base 문서 테이블
CREATE TABLE IF NOT EXISTS knowledge_documents (
    id UUID PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    category VARCHAR(100) NOT NULL,
    product_family VARCHAR(200),
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    status VARCHAR(40) NOT NULL,
    description VARCHAR(2000),
    tags VARCHAR(500),
    uploaded_by VARCHAR(120),
    extracted_text TEXT,
    ocr_confidence DOUBLE PRECISION,
    chunk_count INT,
    vector_count INT,
    last_error VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_kb_docs_category ON knowledge_documents(category);
CREATE INDEX IF NOT EXISTS idx_kb_docs_product_family ON knowledge_documents(product_family);
CREATE INDEX IF NOT EXISTS idx_kb_docs_status ON knowledge_documents(status);
CREATE INDEX IF NOT EXISTS idx_kb_docs_created_at ON knowledge_documents(created_at);

-- document_chunks 테이블에 source 구분 컬럼 추가
ALTER TABLE document_chunks ADD COLUMN IF NOT EXISTS source_type VARCHAR(20) DEFAULT 'INQUIRY';
ALTER TABLE document_chunks ADD COLUMN IF NOT EXISTS source_id UUID;

-- source_id: INQUIRY 타입이면 documents.id, KNOWLEDGE_BASE 타입이면 knowledge_documents.id
-- 기존 데이터의 source_id는 document_id 값으로 세팅
UPDATE document_chunks SET source_id = document_id WHERE source_type = 'INQUIRY';

CREATE INDEX IF NOT EXISTS idx_chunks_source ON document_chunks(source_type, source_id);
```

### 설계 결정

`document_chunks` 테이블을 **공유**한다 (PRD D1 결정사항 A안 채택):
- 별도 `kb_document_chunks` 테이블을 만들지 않음
- `source_type` 컬럼으로 구분 (`INQUIRY` / `KNOWLEDGE_BASE`)
- 벡터 검색 통합이 간단하고, ChunkingService/VectorizingService 재사용 가능

### 수용 기준

- [ ] H2(로컬) + PostgreSQL(Docker) 모두 마이그레이션 성공
- [ ] 기존 `document_chunks` 데이터에 `source_type=INQUIRY`가 세팅됨
- [ ] 기존 Flyway 체크섬 충돌 없음

---

## BE-02. KnowledgeDocument 엔티티 + Repository (P0)

### 작업 내용

**신규 파일:** `backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/knowledge/KnowledgeDocumentJpaEntity.java`

```java
@Entity
@Table(name = "knowledge_documents")
public class KnowledgeDocumentJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 100)
    private String category;       // MANUAL, PROTOCOL, FAQ, SPEC_SHEET

    @Column(name = "product_family", length = 200)
    private String productFamily;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Column(nullable = false, length = 40)
    private String status;         // UPLOADED, PARSING, PARSED, CHUNKED, INDEXED, FAILED

    @Column(length = 2000)
    private String description;

    @Column(length = 500)
    private String tags;

    @Column(name = "uploaded_by", length = 120)
    private String uploadedBy;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "ocr_confidence")
    private Double ocrConfidence;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Column(name = "vector_count")
    private Integer vectorCount;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // 상태 전환 메서드 (DocumentMetadataJpaEntity와 동일 패턴)
    public void markParsing() { ... }
    public void markParsed(String text) { ... }
    public void markParsedFromOcr(String text, double confidence) { ... }
    public void markChunked(int count) { ... }
    public void markIndexed(int count) { ... }
    public void markFailed(String error) { ... }

    // 팩토리 메서드
    public static KnowledgeDocumentJpaEntity create(
        String title, String category, String productFamily,
        String fileName, String contentType, long fileSize,
        String storagePath, String description, String tags,
        String uploadedBy
    ) { ... }
}
```

**신규 파일:** `backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/knowledge/KnowledgeDocumentJpaRepository.java`

```java
public interface KnowledgeDocumentJpaRepository
    extends JpaRepository<KnowledgeDocumentJpaEntity, UUID>,
            JpaSpecificationExecutor<KnowledgeDocumentJpaEntity> {

    List<KnowledgeDocumentJpaEntity> findByStatusIn(List<String> statuses);
    int countByStatus(String status);
}
```

### 수용 기준

- [ ] 엔티티 저장/조회 기본 동작 확인 (DataJpaTest)
- [ ] 상태 전환 메서드가 정확한 상태로 변경

---

## BE-03. Knowledge Base CRUD API (P0)

### API 스펙

#### 문서 업로드

```
POST /api/v1/knowledge-base/documents
Content-Type: multipart/form-data
```

| 파트 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `file` | File | O | PDF/DOC/DOCX 파일 |
| `title` | String | O | 문서 제목 |
| `category` | String | O | `MANUAL` / `PROTOCOL` / `FAQ` / `SPEC_SHEET` |
| `productFamily` | String | X | 제품군 (예: `Reagent`, `Instrument`) |
| `description` | String | X | 문서 설명 |
| `tags` | String | X | 검색 태그 (쉼표 구분) |

**응답 (201 Created):**

```json
{
  "documentId": "uuid",
  "title": "Reagent X 사용 매뉴얼",
  "category": "MANUAL",
  "productFamily": "Reagent",
  "fileName": "reagent_x_manual.pdf",
  "status": "UPLOADED",
  "createdAt": "2026-02-13T12:00:00Z"
}
```

#### 문서 목록 조회

```
GET /api/v1/knowledge-base/documents
    ?page=0&size=20&sort=createdAt,desc
    &category=MANUAL
    &productFamily=Reagent
    &status=INDEXED
    &keyword=reagent
```

**응답 (200 OK):**

```json
{
  "content": [
    {
      "documentId": "uuid",
      "title": "Reagent X 사용 매뉴얼",
      "category": "MANUAL",
      "productFamily": "Reagent",
      "fileName": "reagent_x_manual.pdf",
      "fileSize": 1048576,
      "status": "INDEXED",
      "chunkCount": 42,
      "vectorCount": 42,
      "uploadedBy": "admin",
      "tags": "reagent,manual,4도보관",
      "createdAt": "2026-02-13T12:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 35,
  "totalPages": 2
}
```

#### 문서 상세 조회

```
GET /api/v1/knowledge-base/documents/{docId}
```

전체 메타데이터 + 인덱싱 상태 반환.

#### 문서 삭제

```
DELETE /api/v1/knowledge-base/documents/{docId}
```

- 파일 삭제 + DB 레코드 삭제 + 관련 chunk 삭제 + 벡터 스토어에서 제거
- **주의**: VectorStore 인터페이스에 `deleteByDocumentId()` 메서드 추가 필요

**응답 (204 No Content)**

#### 개별 문서 인덱싱

```
POST /api/v1/knowledge-base/documents/{docId}/indexing/run
```

**응답 (200 OK):**

```json
{
  "documentId": "uuid",
  "status": "INDEXED",
  "chunkCount": 42,
  "vectorCount": 42
}
```

#### 미인덱싱 문서 일괄 인덱싱

```
POST /api/v1/knowledge-base/indexing/run
```

**응답 (200 OK):**

```json
{
  "processed": 5,
  "succeeded": 4,
  "failed": 1
}
```

#### 통계 조회

```
GET /api/v1/knowledge-base/stats
```

**응답 (200 OK):**

```json
{
  "totalDocuments": 35,
  "indexedDocuments": 30,
  "totalChunks": 1250,
  "byCategory": {
    "MANUAL": 15,
    "PROTOCOL": 10,
    "FAQ": 7,
    "SPEC_SHEET": 3
  },
  "byProductFamily": {
    "Reagent": 20,
    "Instrument": 10,
    "Software": 5
  }
}
```

### 백엔드 구현 가이드

**신규 파일:** `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/KnowledgeBaseController.java`

```java
@RestController
@RequestMapping("/api/v1/knowledge-base")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @PostMapping("/documents")
    public ResponseEntity<KbDocumentResponse> upload(
        @RequestPart("file") MultipartFile file,
        @RequestPart("title") String title,
        @RequestPart("category") String category,
        @RequestPart(value = "productFamily", required = false) String productFamily,
        @RequestPart(value = "description", required = false) String description,
        @RequestPart(value = "tags", required = false) String tags
    ) { ... }

    @GetMapping("/documents")
    public ResponseEntity<KbDocumentListResponse> list(
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String productFamily,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
        Pageable pageable
    ) { ... }

    @GetMapping("/documents/{docId}")
    public ResponseEntity<KbDocumentDetailResponse> detail(@PathVariable UUID docId) { ... }

    @DeleteMapping("/documents/{docId}")
    public ResponseEntity<Void> delete(@PathVariable UUID docId) { ... }

    @PostMapping("/documents/{docId}/indexing/run")
    public ResponseEntity<KbIndexingResponse> indexOne(@PathVariable UUID docId) { ... }

    @PostMapping("/indexing/run")
    public ResponseEntity<KbBatchIndexingResponse> indexAll() { ... }

    @GetMapping("/stats")
    public ResponseEntity<KbStatsResponse> stats() { ... }
}
```

**신규 파일:** `backend/app-api/src/main/java/com/biorad/csrag/application/KnowledgeBaseService.java`

```java
@Service
public class KnowledgeBaseService {

    private final KnowledgeDocumentJpaRepository kbDocRepo;
    private final DocumentChunkJpaRepository chunkRepo;
    private final ChunkingService chunkingService;
    private final VectorizingService vectorizingService;
    private final VectorStore vectorStore;
    private final EmbeddingService embeddingService;
    private final OcrService ocrService;

    @Value("${app.storage.upload-dir:uploads}")
    private String uploadDir;

    // ===== 업로드 =====
    @Transactional
    public KbDocumentResponse upload(MultipartFile file, String title,
            String category, String productFamily,
            String description, String tags) {
        // 1. 파일 저장: {uploadDir}/knowledge-base/{docId}_{fileName}
        // 2. KnowledgeDocumentJpaEntity.create() + save
        // 3. 응답 반환
    }

    // ===== 인덱싱 (단일) =====
    @Transactional
    public KbIndexingResponse indexOne(UUID docId) {
        // 1. 문서 조회
        // 2. 텍스트 추출 (기존 DocumentIndexingService.extractText 로직 재사용)
        // 3. OCR 필요 시 처리
        // 4. 청킹: chunkingService 사용, source_type='KNOWLEDGE_BASE' 세팅
        // 5. 벡터화: vectorizingService 사용
        // 6. 상태 업데이트
    }

    // ===== 삭제 =====
    @Transactional
    public void delete(UUID docId) {
        // 1. 관련 chunk 삭제 (DB)
        // 2. 벡터 스토어에서 해당 chunk 삭제
        // 3. 파일 삭제
        // 4. 엔티티 삭제
    }
}
```

### 파이프라인 재사용을 위한 리팩토링

#### DocumentChunkJpaEntity 수정

**수정 파일:** `backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/document/DocumentChunkJpaEntity.java`

```java
// 기존 필드에 추가
@Column(name = "source_type", length = 20)
private String sourceType = "INQUIRY";    // INQUIRY 또는 KNOWLEDGE_BASE

@Column(name = "source_id")
private UUID sourceId;                     // 원본 문서 ID
```

#### ChunkingService 수정

**수정 파일:** `backend/app-api/src/main/java/com/biorad/csrag/application/ChunkingService.java`

기존 `chunkAndStore(UUID documentId, String text)` 메서드를 오버로드:

```java
// 기존 (하위 호환)
public int chunkAndStore(UUID documentId, String text) {
    return chunkAndStore(documentId, text, "INQUIRY", documentId);
}

// 신규
public int chunkAndStore(UUID documentId, String text, String sourceType, UUID sourceId) {
    // 기존 로직 + source_type, source_id 세팅
}
```

#### VectorStore 인터페이스 수정

**수정 파일:** VectorStore 인터페이스

```java
// 기존
void upsert(UUID chunkId, UUID documentId, List<Double> vector, String content);
List<VectorSearchResult> search(List<Double> queryVector, int topK);

// 신규 추가
void deleteByDocumentId(UUID documentId);
```

**MockVectorStore 구현:**

```java
@Override
public void deleteByDocumentId(UUID documentId) {
    store.entrySet().removeIf(e -> e.getValue().documentId().equals(documentId));
}
```

**QdrantVectorStore 구현:**

```java
@Override
public void deleteByDocumentId(UUID documentId) {
    // POST /collections/{collection}/points/delete
    // filter: { must: [{ key: "documentId", match: { value: documentId.toString() } }] }
}
```

### 수용 기준

- [ ] 파일 업로드 시 `knowledge-base/` 디렉토리에 저장된다
- [ ] CRUD API 7개 엔드포인트 모두 정상 동작
- [ ] 인덱싱 실행 시 기존 파이프라인(파싱→청킹→벡터화)이 동작한다
- [ ] chunk에 `source_type=KNOWLEDGE_BASE`가 저장된다
- [ ] 삭제 시 파일 + DB + 벡터 모두 정리된다
- [ ] 기존 inquiry 문서 인덱싱 기능 회귀 없음

---

## BE-04. 통합 검색 — 문의 문서 + 지식 기반 동시 검색 (P0)

### 목적

기존 `AnalysisService.retrieve()`가 inquiry chunk만 검색하던 것을 **전체 chunk(inquiry + knowledge_base)** 에서 검색하도록 확장한다.

### 현재 흐름 (AS-IS)

```java
// AnalysisService.java
private List<EvidenceItem> retrieve(UUID inquiryId, String question, int topK) {
    List<Double> queryVector = embeddingService.embed(question);
    List<VectorSearchResult> results = vectorStore.search(queryVector, topK);
    // → 결과를 EvidenceItem으로 변환
}
```

현재 VectorStore.search()는 **모든 벡터**를 대상으로 검색하므로, Knowledge Base 문서가 인덱싱되면 자동으로 검색 대상에 포함된다.

### 변경 사항 (TO-BE)

**1단계: VectorSearchResult에 sourceType 추가**

**수정 파일:** `VectorSearchResult.java`

```java
public record VectorSearchResult(
    UUID chunkId,
    UUID documentId,
    String content,
    double score,
    String sourceType    // 신규: "INQUIRY" 또는 "KNOWLEDGE_BASE"
) {}
```

**2단계: VectorStore.upsert()에 sourceType 메타데이터 포함**

```java
// upsert 시 payload에 sourceType 추가
void upsert(UUID chunkId, UUID documentId, List<Double> vector, String content, String sourceType);
```

기존 upsert 호출부에 sourceType 파라미터 추가:
- `DocumentIndexingService` → `"INQUIRY"`
- `KnowledgeBaseService` → `"KNOWLEDGE_BASE"`

**3단계: 검색 결과에서 sourceType 읽기**

MockVectorStore:
```java
// VectorRecord에 sourceType 필드 추가
record VectorRecord(UUID chunkId, UUID documentId, List<Double> vector, String content, String sourceType) {}
```

QdrantVectorStore:
```java
// payload에 sourceType 포함하여 저장/조회
```

**4단계: AnalyzeResponse에 sourceType 노출**

**수정 파일:** AnalyzeResponse의 evidence 항목

```java
// 기존 EvidenceItem
record EvidenceItem(UUID chunkId, UUID documentId, double score, String excerpt) {}

// 변경
record EvidenceItem(UUID chunkId, UUID documentId, double score, String excerpt, String sourceType) {}
```

### API 응답 변경

```json
{
  "evidences": [
    {
      "chunkId": "uuid",
      "documentId": "uuid",
      "score": 0.92,
      "excerpt": "Reagent X는 4°C에서 최대 12시간...",
      "sourceType": "KNOWLEDGE_BASE"
    },
    {
      "chunkId": "uuid",
      "documentId": "uuid",
      "score": 0.85,
      "excerpt": "고객 첨부 문서에 따르면...",
      "sourceType": "INQUIRY"
    }
  ]
}
```

### 테스트

**신규 테스트:** `AnalysisServiceIntegrationTest`

```
시나리오: Knowledge Base 문서와 Inquiry 문서가 모두 인덱싱된 상태에서 분석 실행
Given: KB에 "Reagent X 매뉴얼" 인덱싱됨 (3 chunks)
  And: Inquiry에 "고객 실험 보고서" 인덱싱됨 (2 chunks)
When: analyzeInquiry(question="Reagent X 4도 보관")
Then: evidences에 두 출처의 chunk가 모두 포함됨
  And: 각 evidence의 sourceType이 정확히 표시됨
```

### 수용 기준

- [ ] 분석 결과에 KB 문서와 Inquiry 문서 양쪽의 evidence가 포함된다
- [ ] 각 evidence에 `sourceType` 필드가 정확히 표시된다
- [ ] KB 문서가 없는 경우에도 기존과 동일하게 동작한다 (하위 호환)

---

## FE-01. 지식 기반 관리 페이지 (P0)

### 작업 내용

**수정 파일:** `frontend/src/lib/api/client.ts`

#### 신규 타입

```typescript
export interface KbDocument {
  documentId: string;
  title: string;
  category: string;
  productFamily: string | null;
  fileName: string;
  fileSize: number;
  status: string;
  chunkCount: number | null;
  vectorCount: number | null;
  uploadedBy: string | null;
  tags: string | null;
  description: string | null;
  lastError: string | null;
  createdAt: string;
}

export interface KbDocumentListResponse {
  content: KbDocument[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface KbStats {
  totalDocuments: number;
  indexedDocuments: number;
  totalChunks: number;
  byCategory: Record<string, number>;
  byProductFamily: Record<string, number>;
}

// 카테고리 한국어 매핑
export const KB_CATEGORY_LABELS: Record<string, string> = {
  MANUAL:     "매뉴얼",
  PROTOCOL:   "프로토콜",
  FAQ:        "FAQ",
  SPEC_SHEET: "스펙시트",
};
```

#### 신규 API 함수

```typescript
export async function listKbDocuments(params): Promise<KbDocumentListResponse> { ... }
export async function uploadKbDocument(file, metadata): Promise<KbDocument> { ... }
export async function deleteKbDocument(docId: string): Promise<void> { ... }
export async function indexKbDocument(docId: string): Promise<any> { ... }
export async function indexAllKbDocuments(): Promise<any> { ... }
export async function getKbStats(): Promise<KbStats> { ... }
```

**수정 파일:** `frontend/src/app/knowledge-base/page.tsx` (Sprint 7의 placeholder 교체)

### 화면 구조

```
┌─────────────────────────────────────────────────────────┐
│ 지식 기반 관리                              [문서 등록]     │
├─────────────────────────────────────────────────────────┤
│ 통계: 전체 35건 · 인덱싱 완료 30건 · 청크 1,250개           │
├─────────────────────────────────────────────────────────┤
│ [카테고리 ▼] [제품군 ▼] [상태 ▼] [검색어____]     [검색]   │
├──────────────────┬──────┬──────┬──────┬──────┬──────────┤
│ 제목              │카테고리│제품군 │ 상태  │청크수│ 등록일    │
├──────────────────┼──────┼──────┼──────┼──────┼──────────┤
│ Reagent X 매뉴얼  │매뉴얼 │Reagent│인덱싱│  42 │ 02-13    │
│ Protocol Y v2    │프로토콜│  -   │업로드 │  -  │ 02-12    │
├──────────────────┴──────┴──────┴──────┴──────┴──────────┤
│ 전체 35건 중 1-20건    [◀] [1] [2] [▶]    [20건 ▼]       │
└─────────────────────────────────────────────────────────┘
```

**[문서 등록] 버튼 클릭 시:**

```
┌── 문서 등록 ───────────────────────────────┐
│                                           │
│  파일 선택: [드래그 앤 드롭 또는 클릭]       │
│             📄 reagent_x_manual.pdf (2MB)  │
│                                           │
│  제목*:     [Reagent X 사용 매뉴얼        ] │
│  카테고리*: [매뉴얼 ▼]                      │
│  제품군:    [Reagent               ]       │
│  설명:      [선택 입력               ]      │
│  태그:      [reagent, 4도, 보관     ]       │
│                                           │
│         [취소]          [등록]              │
└───────────────────────────────────────────┘
```

**테이블 행 클릭 시 (상세 모달 또는 확장):**

```
┌── Reagent X 사용 매뉴얼 ──────────────────┐
│ 카테고리: 매뉴얼                            │
│ 제품군: Reagent                            │
│ 파일: reagent_x_manual.pdf (2MB)           │
│ 상태: 인덱싱 완료 ✓                         │
│ 청크: 42개 · 벡터: 42개                     │
│ 등록자: admin · 등록일: 2026-02-13 12:00   │
│ 태그: reagent, 4도, 보관                    │
│ 설명: Bio-Rad Reagent X 공식 사용 매뉴얼    │
│                                           │
│  [인덱싱 실행]  [삭제] (확인 다이얼로그)      │
└───────────────────────────────────────────┘
```

### 주요 구현 사항

| 항목 | 상세 |
|------|------|
| 카테고리 필터 | 매뉴얼, 프로토콜, FAQ, 스펙시트 (한국어 라벨) |
| 상태 배지 | `labelDocStatus()` 사용 |
| 삭제 확인 | "정말 삭제하시겠습니까? 관련 벡터 데이터도 함께 삭제됩니다." 확인 다이얼로그 |
| 인덱싱 실행 | 실행 후 상태 자동 갱신 |
| 빈 상태 | "등록된 지식 기반 문서가 없습니다. [문서 등록] 버튼을 눌러 시작하세요." |

### 수용 기준

- [ ] 문서 등록 모달에서 파일 + 메타데이터 입력 후 등록 가능
- [ ] 목록에서 카테고리·제품군·상태 필터가 동작한다
- [ ] 페이징이 동작한다
- [ ] 개별 문서 인덱싱 실행 + 상태 갱신이 동작한다
- [ ] 삭제 시 확인 다이얼로그 → 삭제 완료 토스트
- [ ] 모든 상태·카테고리가 한국어로 표시된다

---

## FE-02. 분석 결과 출처 구분 표시 (P1)

### 목적

분석(Analysis) 결과의 evidence 목록에 **출처 구분**(문의 첨부 / 지식 기반)을 표시한다.

### 작업 내용

**수정 파일:** `frontend/src/lib/api/client.ts`

```typescript
// AnalyzeEvidenceItem에 sourceType 추가
export interface AnalyzeEvidenceItem {
  chunkId: string;
  documentId: string;
  score: number;
  excerpt: string;
  sourceType: "INQUIRY" | "KNOWLEDGE_BASE";  // 신규
}
```

**수정 파일:** `frontend/src/components/inquiry-form.tsx` (또는 Sprint 9에서 분리될 분석 탭 컴포넌트)

evidence 목록 렌더링 부분:

```
현재:
  📎 chunk abc123 — 유사도: 0.92

변경 후:
  📎 [지식 기반] chunk abc123 — 유사도: 0.92
  📎 [문의 첨부] chunk def456 — 유사도: 0.85
```

출처 배지:
- `KNOWLEDGE_BASE` → `[지식 기반]` (파란색 배지)
- `INQUIRY` → `[문의 첨부]` (회색 배지)

### 수용 기준

- [ ] evidence 목록에 출처 배지가 표시된다
- [ ] sourceType이 없는 경우(하위 호환) 배지 미표시

---

## 테스트 전략

### 백엔드

| 테스트 | 파일 | 범위 |
|--------|------|------|
| `KnowledgeDocumentJpaRepositoryTest` | DataJpaTest | CRUD + 필터 쿼리 |
| `KnowledgeBaseServiceTest` | 단위 테스트 | 업로드/인덱싱/삭제 로직 |
| `KnowledgeBaseControllerWebMvcTest` | WebMvcTest | API 스펙 검증 |
| `KbSearchIntegrationTest` | SpringBootTest | KB + Inquiry 통합 검색 |

### 프론트엔드

| 테스트 | 범위 |
|--------|------|
| 문서 등록 → 목록 확인 | E2E 스모크 |
| 필터 + 페이징 | 수동 검증 |
| 인덱싱 실행 → 상태 변경 | 수동 검증 |
| 삭제 → 목록에서 제거 | 수동 검증 |

---

## 실행 순서

```
Week 1:
  1) BE-01  DB 마이그레이션 V13 (0.5일)
  2) BE-02  엔티티 + Repository (1일)
  3) BE-03  CRUD API 전체 구현 (2일)
  4) BE-03  ChunkingService/VectorizingService 리팩토링 (1일)

Week 2:
  5) BE-04  통합 검색 (sourceType 추가) (1.5일)
  6) FE-01  지식 기반 관리 페이지 (2일)
  7) FE-02  분석 결과 출처 구분 (0.5일)
  8) QA     KB 등록 → 인덱싱 → 검색 통합 검증 (1일)
```

---

## 수용 기준 (Sprint 전체)

1. CS 담당자가 **문의 없이** 기술 문서를 등록할 수 있다
2. 등록된 문서가 인덱싱되어 벡터 스토어에 저장된다
3. 분석 시 **문의 첨부 + 지식 기반** 양쪽에서 근거가 검색된다
4. 검색 결과에 출처(문의 첨부 / 지식 기반)가 구분 표시된다
5. Knowledge Base CRUD(등록/목록/상세/삭제/인덱싱) 모두 정상 동작
6. 기존 inquiry 워크플로우(등록→인덱싱→분석→초안→발송) 회귀 없음
7. `./gradlew build` + `npm run build` 모두 성공
