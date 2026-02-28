# PRD: RAG 전처리 고도화 + Agentic AI 통합 — 답변 정확도 혁신

## 문서 정보
- **작성일**: 2026-02-28
- **버전**: v2.0 (리뷰 13건 반영)
- **우선순위**: P0 (답변 정확도 근본 개선)
- **영향 범위**: 전처리 파이프라인 전체 + 검색 + 생성 + 멀티모달
- **예상 기간**: 5 Sprint (각 2주)

---

## 1. Executive Summary

현재 Bio-Rad CS 대응 허브의 RAG 파이프라인은 **전처리 단계의 구조적 결함**으로 인해 답변 정확도가 지속적으로 낮게 측정되고 있다. 코드 심층 분석 결과 `cleanText()`가 모든 문서 구조를 파괴하고, 과학 약어에서 문장 분리가 오동작하며, PDF 테이블이 완전히 무시되는 등 **근본적인 전처리 버그**가 확인되었다.

본 PRD는 전처리 수정부터 시작하여, 2025-2026 최신 RAG 기법(Contextual Retrieval, Reranking, HyDE, Agentic RAG)을 단계적으로 적용하고, 고객이 자주 첨부하는 **이미지(스크린샷, 그래프, 에러 화면)를 분석하는 멀티모달 파이프라인**까지 포함한다.

### 목표 메트릭

| 메트릭 | 현재 (추정) | Sprint 2 이후 | Sprint 5 이후 |
|--------|-----------|-------------|-------------|
| 답변 정확도 (Faithfulness) | ~35% | ≥ 60% | ≥ 85% |
| 검색 정밀도 (Context Precision) | ~40% | ≥ 65% | ≥ 85% |
| 검색 재현율 (Context Recall) | ~45% | ≥ 70% | ≥ 90% |
| 인용 정확도 | ~25% | ≥ 60% | ≥ 85% |
| 이미지 문의 대응률 | 0% | 50% (업로드만) | ≥ 80% |

---

## 2. 현재 시스템 심층 진단

### 2.1 전처리 파이프라인 — Critical 결함 3건

#### BUG-1: `cleanText()`가 문서 구조 완전 파괴 (CRITICAL)

**파일**: `DocumentTextExtractor.java:123-130`

```java
private String cleanText(String text) {
    return text.replaceAll("\\p{Cntrl}", " ")   // \n, \r, \t 모두 제거
            .replaceAll("\\s+", " ")              // 단일 스페이스로 압축
            .trim();
}
```

**영향**:
- 모든 개행(`\n`)이 제거되어 문단 경계 소멸
- `ChunkingService`의 `\n` 기반 문장 분리가 **완전히 Dead Code**
- `HEADING_PATTERN`이 제목을 감지할 수 없음 (제목 앞 개행 없음)
- 테이블 행/열 구조가 단일 문자열로 병합
- 결과: 1500자 청크가 문맥 무시하고 임의 위치에서 잘림

#### BUG-2: 과학 약어에서 문장 오분리 (CRITICAL)

**파일**: `ChunkingService.java:23-25`

```java
private static final Pattern SENTENCE_SPLIT = Pattern.compile(
    "(?<=[.!?。\\n])\\s+"
);
```

**오동작 케이스**:

| 원본 | 분리 결과 | 올바른 동작 |
|------|---------|-----------|
| `final 0.125 uM` | `"final 0."` + `"125 uM"` | 분리 안 함 |
| `(Fig. 2)` | `"(Fig."` + `"2)"` | 분리 안 함 |
| `e.g. restriction` | `"e.g."` + `"restriction"` | 분리 안 함 |
| `p.6 참고` | `"p."` + `"6 참고"` | 분리 안 함 |
| `Dr. Kim` | `"Dr."` + `"Kim"` | 분리 안 함 |
| `1×10^9 GC/ml` | 정상 | - |

Bio-Rad 매뉴얼에 과학 약어, 소수점 수치, 페이지 참조가 매 페이지 등장 → 청크 품질 심각하게 저하.

#### BUG-3: PDF 테이블 미처리 (CRITICAL)

**파일**: `DocumentTextExtractor.java:47-55`

PDFBox `PDFTextStripper`는 테이블을 단순 텍스트 흐름으로 추출:

```
# PDF 원본 (Bio-Rad 스펙 테이블):
┌─────────────────┬──────────┬───────────────┐
│ AAV Serotype    │ 호환 여부  │ Dynamic Range │
├─────────────────┼──────────┼───────────────┤
│ AAV2            │ Yes      │ 10^9 - 10^12  │
│ AAV5            │ Yes      │ 10^9 - 10^12  │
│ AAV8            │ No       │ N/A           │
└─────────────────┴──────────┴───────────────┘

# PDFBox 추출 결과:
"AAV Serotype 호환 여부 Dynamic Range AAV2 Yes 10^9 - 10^12 AAV5 Yes 10^9 ..."
```

열-값 관계가 파괴되어, "AAV2의 Dynamic Range는?" 질문에 정확한 검색 불가.

### 2.2 추가 전처리 문제

| ID | 문제 | 위치 | 심각도 |
|----|------|------|--------|
| PP-4 | 헤더/푸터 반복 텍스트가 청크에 오염 | `DocumentTextExtractor` | HIGH |
| PP-5 | 이미지 업로드 미지원 (PNG/JPG) | `DocumentIndexingWorker` | HIGH |
| PP-6 | PPTX/XLSX 형식 미지원 | `DocumentTextExtractor:26-44` | MEDIUM |
| PP-7 | 임베딩 배치 처리 없음 (청크별 개별 API 호출) | `VectorizingService:52` | MEDIUM |
| PP-8 | Mock 임베딩 16차원 해시 벡터 (개발 무의미) | `MockEmbeddingService:14-24` | MEDIUM |

### 2.3 검색 파이프라인 문제

| ID | 문제 | 위치 | 심각도 |
|----|------|------|--------|
| RT-1 | 리랭킹 없음 — Top-K 결과를 그대로 사용 | `HybridSearchService` | CRITICAL |
| RT-2 | RRF 가중치 고정 (쿼리 유형 무관) | `HybridSearchService:26-36` | HIGH |
| RT-3 | Evidence 평균 스코어 기반 verdict (위치 가중 없음) | `AnalysisService:176-220` | HIGH |
| RT-4 | 쿼리-문서 비대칭 임베딩 없음 | `EmbeddingService` | HIGH |
| RT-5 | 적응형 재검색 없음 (결과 나쁘면 그냥 진행) | `AnswerOrchestrationService` | HIGH |

### 2.4 생성 파이프라인 문제

| ID | 문제 | 위치 | 심각도 |
|----|------|------|--------|
| GN-1 | Evidence 예산 8000자 (복합 질문에 부족) | `OpenAiComposeStep:26` | HIGH |
| GN-2 | 인용 삽입이 리터럴 문자열 매칭 | `DefaultComposeStep:59-85` | HIGH |
| GN-3 | SelfReview 제품명 하드코딩 목록 | `SelfReviewStep:18-23` | MEDIUM |
| GN-4 | 시스템 프롬프트 300줄+ 하드코딩 | `OpenAiComposeStep:320-331` | MEDIUM |
| GN-5 | SubQuestion 매핑 파싱이 취약 | `DefaultComposeStep:216-304` | MEDIUM |

---

## 3. 솔루션 아키텍처 — 목표 파이프라인

```
                         ┌─────────────────────────────────┐
                         │        고객 문의 접수             │
                         │  텍스트 + PDF/DOCX + 이미지(NEW) │
                         └────────────┬────────────────────┘
                                      │
                    ┌─────────────────┴─────────────────┐
                    ▼                                    ▼
        ┌──────────────────┐              ┌──────────────────────┐
        │  문서 전처리       │              │  이미지 분석 (NEW)     │
        │                  │              │                      │
        │ 1. 구조 보존 추출  │              │ 1. VLM 분석           │
        │ 2. 테이블 구조화   │              │   (GPT-4o Vision)    │
        │ 3. 헤더/푸터 제거  │              │ 2. 스크린샷 OCR       │
        │ 4. 시맨틱 청킹    │              │ 3. 그래프/차트 해석    │
        │ 5. 문맥 주입      │              │ 4. 구조화 텍스트 변환  │
        └────────┬─────────┘              └──────────┬───────────┘
                 │                                    │
                 ▼                                    ▼
        ┌──────────────────┐              ┌──────────────────────┐
        │  임베딩 + 인덱싱   │              │  질문 보강 (NEW)       │
        │                  │              │                      │
        │ 1. 비대칭 임베딩   │              │ 이미지 분석 결과를     │
        │ 2. 배치 처리      │              │ 질문 텍스트에 추가     │
        │ 3. Parent-Child  │              └──────────┬───────────┘
        └────────┬─────────┘                         │
                 │              ┌─────────────────────┘
                 ▼              ▼
        ┌──────────────────────────────┐
        │       검색 (Retrieval)        │
        │                              │
        │ 1. HyDE 쿼리 변환            │
        │ 2. 하이브리드 검색 (Vector+BM25)│
        │ 3. Cross-Encoder 리랭킹 (NEW) │
        │ 4. 적응형 재검색 루프 (NEW)    │
        └──────────────┬───────────────┘
                       │
                       ▼
        ┌──────────────────────────────┐
        │       생성 (Generation)       │
        │                              │
        │ 1. CoT 기반 추론 + 답변 생성   │
        │ 2. 인용 충실도 검증 (NEW)      │
        │ 3. Critic Agent 교차 검증 (NEW)│
        │ 4. SelfReview 강화            │
        └──────────────┬───────────────┘
                       │
                       ▼
        ┌──────────────────────────────┐
        │     Human-in-the-Loop        │
        │  DRAFT → REVIEW → APPROVE    │
        └──────────────────────────────┘
```

---

## 4. Sprint 계획

---

### Sprint 1: 전처리 근본 수정 + 이미지 업로드 (2주)

> **목표**: 전처리 Critical 버그 3건 수정 + 이미지 파일 업로드/분석 기반 구축
> **예상 효과**: 검색 정밀도 +25-30%, 이미지 문의 접수 가능

#### TASK 1-1: `cleanText()` 구조 보존 리팩토링

**파일**: `DocumentTextExtractor.java`

**현재** (구조 파괴):
```java
private String cleanText(String text) {
    return text.replaceAll("\\p{Cntrl}", " ")
            .replaceAll("\\s+", " ")
            .trim();
}
```

**변경** (구조 보존):
```java
/**
 * 텍스트 정리 — 구조적 공백(\n, \t)은 보존하면서 제어 문자만 제거
 */
private String cleanText(String text) {
    if (text == null) return "";
    return text
        // 1. \n, \r\n, \t를 제외한 제어 문자만 제거
        .replaceAll("[\\p{Cntrl}&&[^\\n\\r\\t]]", " ")
        // 2. \r\n → \n 정규화
        .replaceAll("\\r\\n", "\n")
        .replaceAll("\\r", "\n")
        // 3. 연속 공백(탭/스페이스만)을 단일 스페이스로 (개행 보존)
        .replaceAll("[\\t ]+", " ")
        // 4. 연속 빈 줄을 단일 빈 줄로
        .replaceAll("\\n{3,}", "\n\n")
        .trim();
}
```

**효과**: `ChunkingService`의 `\n` 기반 분리가 정상 동작, 제목/단락 경계 보존

> **⚠️ 연쇄 영향 분석 (리뷰 #2)**
>
> `cleanText()` 수정 시 하위 코드에 미치는 영향을 반드시 함께 처리해야 한다:
>
> | 영향 코드 | 위치 | 현재 동작 | 수정 후 변화 | 조치 |
> |-----------|------|---------|------------|------|
> | 페이지 텍스트 조인 | `ChunkingService:187` | `Collectors.joining(" ")` — cleanText()가 `\n` 제거하므로 스페이스 조인으로 충분 | `\n` 보존되므로 스페이스 조인 시 이중 줄바꿈 발생 가능 | `joining("\n")` 또는 `joining("\n\n")`으로 변경 |
> | `resolvePageRange()` | `ChunkingService:293-320` | 청크와 원본 텍스트 모두 `\n` 없어서 substring 매칭 동작 | 원본에 `\n` 보존되면 정규화 불일치로 매칭 실패 | 매칭 전 양쪽을 `normalizeForComparison()`으로 정규화 |
> | HEADING_PATTERN | `ChunkingService:15` | `^` 앵커가 줄 시작을 의미하나, `\n` 없어서 문서 시작만 매칭 | `\n` 보존되면 `MULTILINE` 플래그로 정상 동작 | `Pattern.MULTILINE` 플래그 확인 (이미 추가됨) |
> | 기존 단위 테스트 | `ChunkingServiceTest` | `\n` 없는 입력 텍스트로 테스트 | 테스트 입력에 `\n` 포함 필요 | 테스트 케이스 업데이트 필수 |
>
> **검증 체크리스트**:
> - [ ] 페이지 조인 후 `\n\n`이 아닌 `\n\n\n+`이 발생하지 않는지 확인
> - [ ] `resolvePageRange()`가 수정된 cleanText() 이후에도 정확한 페이지 번호 반환
> - [ ] 기존 PDF/DOCX 인덱싱 전체 회귀 테스트 통과

---

#### TASK 1-2: 과학 약어 안전한 문장 분리

**파일**: `ChunkingService.java`

**현재** (오분리):
```java
private static final Pattern SENTENCE_SPLIT = Pattern.compile(
    "(?<=[.!?。\\n])\\s+"
);
```

**변경** (2-Pass 약어 보호 방식):

> **⚠️ 설계 주의 (리뷰 #1)**: 기존 v1.0에서 `ABBREVIATION` 패턴을 선언만 하고 `splitIntoSentences()` 내부에서 실제로 사용하지 않는 **Critical 버그**가 있었음. v2.0에서는 **2-Pass Replace/Restore** 방식으로 약어를 확실히 보호함.

```java
// ─── 과학 약어 보호 패턴 ───
// 마침표가 문장 종결이 아닌 약어/소수점 패턴
private static final Pattern ABBREVIATION = Pattern.compile(
    "(?:e\\.g|i\\.e|Fig|Figs|et al|vs|Vol|No|Dr|Mr|Mrs|Prof|approx|" +
    "Inc|Ltd|Corp|Rev|dept|cf|viz|" +
    // Bio-Rad 특화: 단위, 페이지 참조
    "p|pp|sec|min|hr|conc|temp|vol|wt|" +
    // 소수점 숫자 보호: "0.125", "1.5"
    "\\d)" +
    "\\.(?=\\s)"   // 마침표 뒤 공백이 올 때만 매칭
);

// 치환용 플레이스홀더 (텍스트에 등장하지 않는 유니코드)
private static final String DOT_PLACEHOLDER = "\uFFF0";

List<String> splitIntoSentences(String text) {
    if (text == null || text.isBlank()) return List.of();

    // ── Pass 1: 약어의 마침표를 플레이스홀더로 치환 (보호) ──
    String protected_ = ABBREVIATION.matcher(text)
        .replaceAll(m -> m.group().replace(".", DOT_PLACEHOLDER));

    // ── 단락 분리 → 문장 분리 ──
    String[] paragraphs = protected_.split("\\n\\n+");
    List<String> sentences = new ArrayList<>();

    for (String paragraph : paragraphs) {
        // 문장 종결 마침표 + 공백 + 대문자/한글/숫자로 시작하는 다음 문장
        String[] parts = paragraph.trim().split(
            "(?<=[.!?。])\\s+(?=[A-Z가-힣\\d(\\[\"'])"
        );
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                // ── Pass 2: 플레이스홀더를 원래 마침표로 복원 ──
                sentences.add(trimmed.replace(DOT_PLACEHOLDER, "."));
            }
        }
    }
    return sentences;
}
```

**검증 케이스**:

| 입력 | v1.0 (버그) | v2.0 (수정) |
|------|-----------|-----------|
| `"final 0.125 uM"` | `"final 0."` + `"125 uM"` | `"final 0.125 uM"` (분리 안 함) |
| `"(Fig. 2) 참조"` | `"(Fig."` + `"2) 참조"` | `"(Fig. 2) 참조"` (분리 안 함) |
| `"효소 처리. Vortex 필요."` | 정상 분리 | `"효소 처리."` + `"Vortex 필요."` (정상) |
| `"e.g. restriction enzyme"` | `"e.g."` + `"restriction"` | `"e.g. restriction enzyme"` (분리 안 함) |

---

#### TASK 1-3: PDF 테이블 구조화 추출

**신규 의존성**: Apache PDFBox의 테이블 추출 한계를 보완하기 위해 **Tabula-java** 도입

**`build.gradle`**:
```gradle
implementation 'technology.tabula:tabula:1.0.5'
```

**신규 파일**: `TableExtractorService.java`

```java
@Service
public class TableExtractorService {

    /**
     * PDF에서 테이블을 감지하여 Markdown 형식으로 변환.
     * 각 테이블은 페이지 번호와 함께 반환된다.
     */
    public List<ExtractedTable> extractTables(Path pdfPath) { ... }

    public record ExtractedTable(
        int pageNumber,
        String markdownTable,    // "| Serotype | Compatible | Range |\n|---|---|---|\n| AAV2 | Yes | ..."
        int rowCount,
        int colCount
    ) {}
}
```

**`DocumentTextExtractor` 변경**:
```java
public List<PageText> extractByPage(Path filePath, String contentType) throws IOException {
    if (normalized.contains("pdf")) {
        List<PageText> textPages = extractPdfByPage(filePath);
        // NEW: 테이블 추출 후 해당 페이지 텍스트에 Markdown 테이블 병합
        List<ExtractedTable> tables = tableExtractor.extractTables(filePath);
        return mergeTablesIntoPages(textPages, tables);
    }
    // ...
}
```

**효과**: Bio-Rad 스펙 시트의 농도표, 호환성 매트릭스, 파라미터 테이블이 구조적으로 청크에 포함

> **⚠️ Tabula-java 한계 (리뷰 #12)**
>
> Tabula-java는 **텍스트 기반 PDF**에서만 테이블을 추출할 수 있다. **스캔된 이미지 PDF** (Bio-Rad 구형 매뉴얼에 간혹 존재)에서는 테이블을 감지하지 못한다.
>
> **폴백 전략**:
> ```
> 1차: Tabula-java (텍스트 PDF) → 성공 시 Markdown 테이블 반환
> 2차: 실패 시 → Sprint 1 TASK 1-5의 VLM(GPT-4o Vision)으로 해당 페이지 이미지를 캡처하여 테이블 분석
> 3차: VLM도 실패 시 → PDFBox 기본 텍스트 추출 (현행과 동일, 열-값 관계 손실 감수)
> ```
>
> **구현**:
> ```java
> public List<ExtractedTable> extractTables(Path pdfPath) {
>     List<ExtractedTable> tables = extractWithTabula(pdfPath);
>     if (tables.isEmpty() && isScannedPdf(pdfPath)) {
>         // 스캔 PDF: VLM 기반 테이블 추출로 폴백
>         tables = extractWithVlm(pdfPath);
>     }
>     return tables;
> }
> ```

---

#### TASK 1-4: 헤더/푸터 반복 텍스트 제거

**신규 메서드**: `DocumentTextExtractor`

```java
/**
 * PDF 페이지들에서 반복되는 헤더/푸터를 감지하여 제거.
 * 전체 페이지의 50% 이상에서 동일한 첫/마지막 줄이 반복되면 헤더/푸터로 판단.
 */
private List<PageText> removeHeadersFooters(List<PageText> pages) {
    if (pages.size() < 3) return pages;

    // 각 페이지의 첫 줄과 마지막 줄 수집
    Map<String, Integer> firstLineFreq = new HashMap<>();
    Map<String, Integer> lastLineFreq = new HashMap<>();

    for (PageText page : pages) {
        String[] lines = page.text().split("\\n");
        if (lines.length > 0) firstLineFreq.merge(lines[0].trim(), 1, Integer::sum);
        if (lines.length > 1) lastLineFreq.merge(lines[lines.length - 1].trim(), 1, Integer::sum);
    }

    int threshold = pages.size() / 2;
    Set<String> headerLines = firstLineFreq.entrySet().stream()
        .filter(e -> e.getValue() >= threshold)
        .map(Map.Entry::getKey).collect(Collectors.toSet());
    Set<String> footerLines = lastLineFreq.entrySet().stream()
        .filter(e -> e.getValue() >= threshold)
        .map(Map.Entry::getKey).collect(Collectors.toSet());

    // 필터링된 PageText 반환
    return pages.stream().map(page -> removeLines(page, headerLines, footerLines)).toList();
}
```

---

#### TASK 1-5: 이미지 업로드 지원 (PNG, JPG, WEBP)

**변경 범위**:

**(A) 백엔드 — 파일 업로드 확장**

**파일**: `FileUploadValidator.java` 수정

```java
// 현재 허용 타입
private static final Set<String> ALLOWED_TYPES = Set.of(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
);

// 변경: 이미지 타입 추가
private static final Set<String> ALLOWED_TYPES = Set.of(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "image/png",
    "image/jpeg",
    "image/webp"
);

private static final long MAX_IMAGE_SIZE = 20 * 1024 * 1024; // 20MB
```

**(B) 백엔드 — VLM 이미지 분석 서비스 (신규)**

**신규 파일**: `ImageAnalysisService.java`

```java
@Service
public class ImageAnalysisService {

    /**
     * 이미지를 VLM(GPT-4o)으로 분석하여 구조화된 텍스트 설명을 생성.
     *
     * 분석 항목:
     * 1. 이미지 유형 분류 (스크린샷/그래프/사진/다이어그램)
     * 2. 텍스트 추출 (OCR) — 에러 메시지, UI 텍스트
     * 3. 시각적 요소 해석 — 그래프 패턴, 차트 수치
     * 4. 기술적 컨텍스트 — Bio-Rad 제품/소프트웨어 식별
     */
    public ImageAnalysisResult analyze(Path imagePath) { ... }

    public record ImageAnalysisResult(
        String imageType,           // SCREENSHOT, GRAPH, PHOTO, DIAGRAM
        String extractedText,       // OCR로 추출된 텍스트
        String visualDescription,   // 시각적 요소 설명
        String technicalContext,    // Bio-Rad 제품/소프트웨어 컨텍스트
        String suggestedQuery,      // 이미지 기반 추천 검색 쿼리
        double confidence           // 분석 신뢰도
    ) {}
}
```

**VLM 프롬프트 (GPT-4o Vision)**:
```
You are a Bio-Rad technical support image analyst.

Analyze this image and provide:

1. **Image Type**: SCREENSHOT | GRAPH | PHOTO | DIAGRAM
2. **Extracted Text**: All visible text, error messages, labels, values
3. **Visual Description**: What the image shows (amplification curves, gel images, plate layouts, software UI, instrument photos)
4. **Technical Context**: Identify any Bio-Rad product, software (CFX Maestro, QX Manager, ddPCR QuantaSoft), instrument model, or experiment type visible
5. **Suggested Query**: What technical question is this image likely related to?

For amplification curves: describe curve shapes (exponential, flat, late Ct), number of targets, threshold lines
For error messages: extract the exact error text and stack trace
For plate layouts: describe well configuration and sample types
For gel images: describe band positions, sizes, and intensities

Respond in Korean.
```

**(C) 이미지 분석 결과의 질문 보강 (NEW)**

**파일**: `AnswerOrchestrationService.java` 수정

```java
// 문의에 이미지가 첨부된 경우, 분석 결과를 질문에 추가
String enrichedQuestion = question;
List<DocumentMetadata> images = documents.stream()
    .filter(d -> d.getContentType().startsWith("image/"))
    .toList();

if (!images.isEmpty()) {
    StringBuilder imageContext = new StringBuilder();
    imageContext.append("\n\n[첨부 이미지 분석 결과]\n");
    for (DocumentMetadata img : images) {
        ImageAnalysisResult result = imageAnalysisService.analyze(img.getFilePath());
        imageContext.append(String.format(
            "- 이미지 유형: %s\n- 추출 텍스트: %s\n- 설명: %s\n- 제품/소프트웨어: %s\n\n",
            result.imageType(), result.extractedText(),
            result.visualDescription(), result.technicalContext()
        ));
    }
    enrichedQuestion = question + imageContext.toString();
}
```

**(D) 프론트엔드 — 이미지 업로드 UI**

**파일**: `InquiryCreateForm.tsx` 수정

```
변경사항:
1. 파일 입력의 accept 속성에 이미지 타입 추가
   accept=".pdf,.docx,image/png,image/jpeg,image/webp"
2. 이미지 미리보기 썸네일 표시
3. 이미지 분석 상태 표시 (분석 중 / 완료 / 실패)
4. 분석 결과 요약을 문의 작성 폼에 자동 표시
```

**(E) DB 마이그레이션**

> **⚠️ 마이그레이션 번호 관리 (리뷰 #11)**: 현재 Flyway 마이그레이션은 V14까지 사용 중. 본 PRD에서 V15-V17을 할당한다. 병렬 개발 시 번호 충돌 방지를 위해 `application.yml`에 `spring.flyway.out-of-order=true` 설정 추가 권장. Sprint 시작 전 최신 마이그레이션 번호를 반드시 확인할 것.

```sql
-- V15__add_image_support.sql
-- document_metadata에 이미지 분석 결과 컬럼 추가
ALTER TABLE document_metadata ADD COLUMN image_analysis_type VARCHAR(20);
ALTER TABLE document_metadata ADD COLUMN image_extracted_text TEXT;
ALTER TABLE document_metadata ADD COLUMN image_visual_description TEXT;
ALTER TABLE document_metadata ADD COLUMN image_technical_context TEXT;
```

---

#### TASK 1-6: `HEADING_PATTERN` 확장

**파일**: `ChunkingService.java`

```java
// 현재: Markdown, 번호, 대문자만 매칭
private static final Pattern HEADING_PATTERN = Pattern.compile(
    "^(?:#{1,6}\\s|\\d+\\.\\s|[A-Z][A-Z\\s]{2,}$)"
);

// 변경: Bio-Rad 매뉴얼 패턴 추가
private static final Pattern HEADING_PATTERN = Pattern.compile(
    "^(?:" +
    "#{1,6}\\s|" +                           // Markdown: ## Title
    "\\d+\\.\\d*\\s|" +                      // 번호: 1. 또는 2.1
    "[A-Z][A-Z\\s]{2,}$|" +                  // 전체 대문자: INTRODUCTION
    "(?:Section|Chapter|Part|APPENDIX)\\s|" + // 영문 섹션
    "(?:Table|Figure|Fig\\.)\\s+\\d|" +       // 표/그림 캡션
    "(?:Troubleshooting|Protocol|Procedure|Safety|Warning|Caution|Note)\\b|" + // Bio-Rad 특화
    "[가-힣]+\\s*:\\s*$" +                    // 한국어 제목: "프로토콜:"
    ")", Pattern.MULTILINE
);
```

---

### Sprint 1 산출물

| 산출물 | 파일 | 유형 |
|--------|------|------|
| 구조 보존 텍스트 추출 | `DocumentTextExtractor.java` | 수정 |
| 과학 약어 안전 문장 분리 | `ChunkingService.java` | 수정 |
| PDF 테이블 구조화 추출 | `TableExtractorService.java` | 신규 |
| 헤더/푸터 제거 | `DocumentTextExtractor.java` | 수정 |
| 이미지 업로드 + VLM 분석 | `ImageAnalysisService.java` | 신규 |
| 파일 업로드 확장 | `FileUploadValidator.java` | 수정 |
| 프론트엔드 이미지 업로드 | `InquiryCreateForm.tsx` | 수정 |
| 제목 패턴 확장 | `ChunkingService.java` | 수정 |
| DB 마이그레이션 | `V15__add_image_support.sql` | 신규 |

### Sprint 1 검증 기준

- [ ] `cleanText()` 이후 `\n`이 보존되는지 단위 테스트
- [ ] `"final 0.125 uM"` 오분리 안 되는지 단위 테스트
- [ ] Bio-Rad 실 PDF에서 테이블이 Markdown으로 변환되는지 검증
- [ ] 이미지 업로드 → VLM 분석 → 분석 결과 저장 E2E 확인
- [ ] 기존 PDF/DOCX 인덱싱이 정상 동작하는지 회귀 테스트

---

### Sprint 2: 임베딩 업그레이드 + 리랭킹 도입 (2주)

> **목표**: 검색 정확도를 근본적으로 개선
> **예상 효과**: 검색 실패 -40%, 검색 정밀도 +33%

#### TASK 2-1: `text-embedding-3-small` → `text-embedding-3-large` 업그레이드

**파일**: `application.yml`

```yaml
# 현재
openai:
  model:
    embedding: text-embedding-3-small

# 변경
openai:
  model:
    embedding: text-embedding-3-large
```

**추가 작업**:
- Qdrant 컬렉션 차원 변경 (1536 → 3072)
- 기존 문서 전체 재인덱싱 필요
- `MockEmbeddingService` 차원도 16 → 3072로 업데이트

> **⚠️ 한영 혼합 임베딩 품질 (리뷰 #10)**
>
> `text-embedding-3-large`는 영어 최적화 모델이다. Bio-Rad 매뉴얼은 **영어 원문 + 한국어 질문** 조합이 빈번하므로, 전환 전 반드시 한영 교차 검색 품질을 검증해야 한다.
>
> **검증 방법**:
> 1. 영어 매뉴얼 청크 20개 + 한국어 질문 20개 세트 준비
> 2. `text-embedding-3-small` vs `text-embedding-3-large`로 각각 코사인 유사도 Top-5 비교
> 3. 한영 교차 검색에서 `3-large`가 오히려 나빠지면 → **BGE-M3** (다국어 특화) 또는 **Voyage-3** 검토
>
> **판단 기준**: 한영 교차 검색 Top-5 관련성이 `3-small` 대비 ≥90% 유지되어야 전환 진행

> **⚠️ 재인덱싱 제로다운타임 전략 (리뷰 #8)**
>
> 임베딩 차원 변경(1536→3072)은 기존 벡터와 호환되지 않아 **전체 재인덱싱이 필수**다. 운영 중 서비스 중단 없이 전환하기 위한 전략:
>
> ```
> Phase 1: 새 컬렉션 생성 (Blue-Green)
>   qdrant_chunks_v2 (3072차원) 생성, 기존 qdrant_chunks_v1 (1536차원) 유지
>
> Phase 2: 백그라운드 재인덱싱
>   비동기 Job으로 기존 청크를 새 임베딩으로 변환 → v2에 저장
>   진행률을 ops/metrics에 노출 (재인덱싱 진행률: 45/120 문서)
>
> Phase 3: 이중 검색 (전환 기간)
>   검색 시 v1 + v2 모두 조회 → 결과 병합
>   v2 인덱싱 완료 문서는 v2 결과만 사용
>
> Phase 4: 전환 완료
>   v2 인덱싱 100% → 검색을 v2 전용으로 전환
>   v1 컬렉션 삭제
> ```
>
> **예상 재인덱싱 시간**: 문서 100개 기준 약 2-4시간 (배치 임베딩 TASK 2-5 적용 시)

---

#### TASK 2-2: 비대칭 임베딩 인터페이스 도입

**파일**: `EmbeddingService.java` 수정

```java
public interface EmbeddingService {

    // 기존 (하위 호환)
    List<Double> embed(String text);

    // NEW: 문서/쿼리 구분 임베딩
    default List<Double> embedDocument(String text) {
        return embed(text);
    }

    default List<Double> embedQuery(String text) {
        return embed(text);
    }
}
```

**`VectorizingService.java`** — 인덱싱 시:
```java
List<Double> vector = embeddingService.embedDocument(chunk.getContent());
```

**검색 시 (`HybridSearchService` 등)**:
```java
List<Double> queryVector = embeddingService.embedQuery(question);
```

**향후 Cohere/Voyage 전환 시**: `embedDocument()`와 `embedQuery()`에서 각각 `input_type` 파라미터를 다르게 전달하면 됨.

---

#### TASK 2-3: HyDE (Hypothetical Document Embeddings) 적용

**신규 파일**: `HydeQueryTransformer.java`

```java
@Service
public class HydeQueryTransformer {

    private final OpenAiClient openAiClient;
    private final EmbeddingService embeddingService;

    /**
     * 사용자 질문을 "가상의 이상적 답변"으로 변환한 후 임베딩.
     * 검색 시 쿼리 임베딩 대신 HyDE 임베딩을 사용하면 문서와의 유사도가 향상됨.
     *
     * 예시:
     *   질문: "restriction enzyme 처리 필요한가요?"
     *   HyDE: "gDNA 사용 시 restriction enzyme으로 사전 처리를 권장합니다.
     *          mixture 제작 전에 처리하며, 최적 조건은 37°C에서 1시간입니다."
     *   → 이 텍스트의 임베딩으로 검색하면 실제 매뉴얼 청크와 더 잘 매칭
     */
    public List<Double> transformAndEmbed(String question, String productContext) {
        String hypotheticalAnswer = generateHypotheticalAnswer(question, productContext);
        return embeddingService.embedQuery(hypotheticalAnswer);
    }

    private String generateHypotheticalAnswer(String question, String productContext) {
        String prompt = String.format("""
            당신은 Bio-Rad 기술지원 전문가입니다.
            다음 질문에 대해 기술 매뉴얼에 있을 법한 답변을 3-5문장으로 작성하세요.
            구체적인 수치, 절차, 조건을 포함하세요. 추측이어도 괜찮습니다.

            제품 컨텍스트: %s
            질문: %s
            """, productContext, question);

        return openAiClient.chat(prompt, 0.7); // 약간 높은 temperature로 다양한 용어 포함
    }
}
```

**적용 위치**: `DefaultRetrieveStep.execute()`에서 벡터 검색 시 HyDE 임베딩 사용

```java
// 기존
List<Double> queryVector = embeddingService.embed(question);

// 변경
List<Double> queryVector = hydeTransformer.transformAndEmbed(question, productContext);
```

> **⚠️ HyDE + 비대칭 임베딩 충돌 해결 (리뷰 #3)**
>
> HyDE는 "가상 답변 텍스트"를 생성한 후 임베딩하는데, 이 가상 답변은 **문서처럼 생긴 텍스트**다. 따라서 비대칭 임베딩(`embedDocument` vs `embedQuery`)에서 어느 모드를 사용할지 혼동이 발생한다.
>
> **결정 매트릭스**:
>
> | 검색 방식 | 임베딩 대상 | 사용할 메서드 | 이유 |
> |-----------|-----------|-------------|------|
> | 일반 검색 (HyDE 미사용) | 사용자 질문 원문 | `embedQuery()` | 질문 텍스트 |
> | HyDE 검색 | 가상 답변 텍스트 | `embedDocument()` | 가상 답변은 문서와 동일한 문체 → document space에서 검색 |
> | BM25 키워드 검색 | 질문 원문 | N/A (텍스트 매칭) | 임베딩 불필요 |
>
> **코드 수정**:
> ```java
> public List<Double> transformAndEmbed(String question, String productContext) {
>     String hypotheticalAnswer = generateHypotheticalAnswer(question, productContext);
>     // HyDE 텍스트는 문서 형태이므로 embedDocument() 사용
>     return embeddingService.embedDocument(hypotheticalAnswer);
> }
> ```
>
> **Cohere/Voyage 전환 시**: `input_type=search_document`로 HyDE 텍스트를 임베딩해야 정확도 최대화

---

#### TASK 2-4: Cross-Encoder 리랭킹 도입

**근거**: 2025년 벤치마크에서 리랭킹 추가만으로 **RAG 정확도 33-40% 향상**, 추가 지연 약 120ms

**신규 파일**: `RerankingService.java`

```java
@Service
public class RerankingService {

    /**
     * 하이브리드 검색 결과를 Cross-Encoder로 리랭킹.
     *
     * 흐름:
     *   1. HybridSearch에서 Top-50 결과 검색
     *   2. Cross-Encoder가 (query, document) 쌍의 관련성 점수 재계산
     *   3. 재정렬된 Top-10 결과 반환
     */
    public List<RerankResult> rerank(String query, List<HybridSearchResult> candidates, int topK) {
        // OpenAI 또는 Cohere Rerank API 호출
        // Cohere Rerank 4가 현재 최고 성능 (1627 ELO)
    }

    public record RerankResult(
        String chunkId,
        String documentId,
        String content,
        double originalScore,      // 하이브리드 검색 원점수
        double rerankScore,        // Cross-Encoder 리랭킹 점수
        String sourceType,
        String productFamily,
        Integer pageStart,
        Integer pageEnd
    ) {}
}
```

**파이프라인 변경**:
```
현재:  HybridSearch(Top-10) → AnalysisService
변경:  HybridSearch(Top-50) → RerankingService(Top-10) → AnalysisService
```

---

#### TASK 2-5: 임베딩 배치 처리

**파일**: `VectorizingService.java` 수정

```java
public int upsertDocumentChunks(UUID documentId) {
    List<DocumentChunkJpaEntity> chunks = chunkRepository
        .findByDocumentIdOrderByChunkIndexAsc(documentId);

    // NEW: 배치 임베딩 (최대 50개씩 한 번에 API 호출)
    List<List<DocumentChunkJpaEntity>> batches = partition(chunks, 50);
    for (List<DocumentChunkJpaEntity> batch : batches) {
        List<String> texts = batch.stream()
            .map(DocumentChunkJpaEntity::getContent).toList();
        List<List<Double>> vectors = embeddingService.embedBatch(texts);
        // upsert 처리...
    }
}
```

**`EmbeddingService` 인터페이스 추가**:
```java
default List<List<Double>> embedBatch(List<String> texts) {
    return texts.stream().map(this::embed).toList(); // 기본: 순차 호출
}
```

---

#### TASK 2-6: Evidence Verdict 로직 개선 (위치 가중)

**파일**: `AnalysisService.java`

```java
// 현재: 단순 평균
double avg = evidences.stream().mapToDouble(EvidenceItem::score).average().orElse(0d);

// 변경: 위치 가중 점수 (상위 결과에 더 높은 가중치)
double weightedScore = 0.0;
double weightSum = 0.0;
for (int i = 0; i < evidences.size(); i++) {
    double weight = 1.0 / (i + 1);  // 1위: 1.0, 2위: 0.5, 3위: 0.33, ...
    weightedScore += evidences.get(i).score() * weight;
    weightSum += weight;
}
double score = weightSum > 0 ? weightedScore / weightSum : 0.0;
```

---

### Sprint 2 산출물

| 산출물 | 파일 | 유형 |
|--------|------|------|
| 임베딩 모델 업그레이드 | `application.yml` | 수정 |
| 비대칭 임베딩 인터페이스 | `EmbeddingService.java` | 수정 |
| HyDE 쿼리 변환 | `HydeQueryTransformer.java` | 신규 |
| Cross-Encoder 리랭킹 | `RerankingService.java` | 신규 |
| 배치 임베딩 | `VectorizingService.java` | 수정 |
| Verdict 가중 점수 | `AnalysisService.java` | 수정 |

### Sprint 2 검증 기준

- [ ] 기존 질문 세트에서 검색 정밀도 측정 (Before/After)
- [ ] HyDE 적용 시 검색 결과 Top-10 관련성 향상 확인
- [ ] 리랭킹 후 Top-5 결과 품질 비교
- [ ] 배치 임베딩으로 인덱싱 속도 개선 확인 (10x 이상)
- [ ] 전체 재인덱싱 정상 완료 확인

---

### Sprint 3: Contextual Retrieval + 시맨틱 청킹 (2주)

> **목표**: Anthropic의 Contextual Retrieval 기법으로 검색 실패를 근본적으로 감소
> **예상 효과**: 검색 실패 -49% (Anthropic 벤치마크 기준)

#### TASK 3-1: Contextual Chunking — 청크에 문맥 주입

**핵심 개념**: 각 청크를 인덱싱하기 전에, LLM이 해당 청크에 대한 문맥 설명을 앞에 추가

**예시**:
```
# 원본 청크 (문맥 없음):
"권장 primer 농도는 final 0.125-1 uM이며, vortex 처리가 필요합니다."

# Contextual 청크 (문맥 주입 후):
"[문맥: 이 단락은 naica 10x multiplex ddPCR mix 매뉴얼(p.6)의
 'Reaction Setup' 섹션에 포함되어 있으며, gDNA 사용 시 primer/probe
 준비 방법을 설명합니다.]
 권장 primer 농도는 final 0.125-1 uM이며, vortex 처리가 필요합니다."
```

**신규 파일**: `ContextualChunkEnricher.java`

```java
@Service
public class ContextualChunkEnricher {

    private static final String CONTEXT_PROMPT = """
        <document>
        {WHOLE_DOCUMENT}
        </document>

        다음은 위 문서에서 추출한 하나의 청크입니다:
        <chunk>
        {CHUNK_CONTENT}
        </chunk>

        이 청크의 문맥을 1-2문장으로 요약하세요.
        문서 제목, 섹션명, 제품명, 이 청크가 설명하는 내용의 맥락을 포함하세요.
        """;

    /**
     * 문서의 청크들에 문맥 설명을 prepend.
     * 인덱싱 비용 증가 (청크 수 × LLM 호출) 대신 검색 품질 대폭 향상.
     *
     * 최적화: 동일 문서의 청크들은 document context를 캐싱하여 재사용.
     */
    public List<EnrichedChunk> enrichChunks(
        String documentText, List<DocumentChunkJpaEntity> chunks, String fileName
    ) { ... }

    public record EnrichedChunk(
        UUID chunkId,
        String originalContent,
        String contextPrefix,      // LLM이 생성한 문맥 설명
        String enrichedContent     // contextPrefix + originalContent
    ) {}
}
```

**`VectorizingService` 변경**:
```java
// 인덱싱 시 enrichedContent로 임베딩
String textToEmbed = enricher.enrich(chunk, documentText, fileName).enrichedContent();
List<Double> vector = embeddingService.embedDocument(textToEmbed);
```

**비용 분석 (리뷰 #4 — 수정)**:

> **⚠️ v1.0에서 Contextual Enrichment 비용을 $15/월로 추정했으나, 이는 5-15배 과소평가였다.**
>
> **정확한 비용 계산**:
> - 문서 100건/월 × 평균 30페이지 × 평균 15청크/문서 = **~1,500 LLM 호출/월**
> - 각 호출에 문서 전체 텍스트(~8K tokens) + 청크(~500 tokens) 전달
> - `gpt-4o-mini` 입력: $0.15/1M tokens → 1,500 × 8,500 tokens = ~12.75M tokens → **~$1.91/월 (입력)**
> - `gpt-4o-mini` 출력: $0.60/1M tokens → 1,500 × 100 tokens = ~150K tokens → **~$0.09/월 (출력)**
> - KB 문서 초기 인덱싱 (일회성): 기존 KB 문서 500건 재인덱싱 → **~$10 일회성**
>
> **월간 비용: ~$2-3/월** (gpt-4o-mini 사용 시)
> **단, `gpt-4o` 사용 시: ~$75-225/월** (토큰당 가격 50배 차이)
>
> **결정**: `gpt-4o-mini`로 충분한 품질이 나오는지 Sprint 3 첫 주에 A/B 테스트 실시. 품질 부족 시 `gpt-4o` 전환 가능 (비용 예산 여유 있음)

**비용 최적화 전략**:
- `gpt-4o-mini` 우선 사용 (저비용, 품질 A/B 테스트 후 결정)
- **배치 생성**: 동일 문서의 청크들을 하나의 프롬프트에 5-10개씩 묶어 문맥 동시 생성 (API 호출 수 1/5~1/10 감소)
- **캐싱**: 동일 문서 재인덱싱 시 기존 context 재사용 (`context_prefix` 컬럼에 저장)
- **점진적 인덱싱**: 신규 문서만 Contextual Enrichment 적용, 기존 문서는 백그라운드 배치로 처리

---

#### TASK 3-2: Parent-Child 이중 인덱싱

**개념**: 작은 자식 청크로 정밀 검색하되, LLM에는 더 큰 부모 청크를 전달

```
Parent Chunk (1500자): 전체 섹션 "Reaction Setup"
  └─ Child Chunk 1 (400자): primer/probe 농도 관련 단락
  └─ Child Chunk 2 (400자): template 준비 관련 단락
  └─ Child Chunk 3 (400자): thermal cycling 조건 단락
```

**검색 흐름**:
1. 쿼리로 Child 청크를 검색 (정밀 매칭)
2. 매칭된 Child의 Parent 청크를 LLM에 전달 (풍부한 문맥)

**DB 마이그레이션**:
```sql
-- V16__add_parent_child_chunks.sql
ALTER TABLE document_chunks ADD COLUMN parent_chunk_id UUID;
ALTER TABLE document_chunks ADD COLUMN chunk_level VARCHAR(10) DEFAULT 'PARENT';
-- chunk_level: 'PARENT' | 'CHILD'

CREATE INDEX idx_chunks_parent_id ON document_chunks(parent_chunk_id);
CREATE INDEX idx_chunks_level ON document_chunks(chunk_level);
```

**`ChunkingService` 변경**:
```java
// 1단계: Parent 청크 생성 (기존 1500자)
List<DocumentChunkJpaEntity> parentChunks = createParentChunks(text);

// 2단계: 각 Parent를 400자 Child로 분할
for (DocumentChunkJpaEntity parent : parentChunks) {
    List<DocumentChunkJpaEntity> children = splitIntoChildren(parent, 400);
    children.forEach(child -> child.setParentChunkId(parent.getId()));
}
```

> **⚠️ Parent-Child × Contextual Enrichment 조합 설계 (리뷰 #5)**
>
> Parent-Child 청킹과 Contextual Enrichment를 동시에 적용할 때, **어느 레벨에 무엇을 적용하는지** 명확히 정의해야 한다.
>
> **조합 매트릭스**:
>
> | 청크 레벨 | Context Prefix | 벡터 인덱싱 | BM25 인덱싱 | LLM에 전달 |
> |-----------|---------------|------------|------------|-----------|
> | **Parent** (1500자) | ✅ 부여 | ❌ | ❌ | ✅ (검색 후 LLM 컨텍스트) |
> | **Child** (400자) | ✅ Parent의 context 상속 | ✅ enriched_content로 | ✅ enriched_content로 | ❌ |
>
> **설계 결정**:
> 1. **Context Prefix는 Parent 단위로 1회만 생성** → Child는 Parent의 context를 상속 (비용 절감)
> 2. **벡터 인덱싱은 Child의 `context_prefix + content`로** → 정밀 검색
> 3. **LLM에는 매칭된 Child의 Parent를 전달** → 풍부한 문맥
> 4. Parent는 DB에만 저장, 벡터 스토어에는 저장하지 않음 (스토리지 절감)
>
> ```java
> // Contextual Enrichment + Parent-Child 통합 흐름
> for (DocumentChunkJpaEntity parent : parentChunks) {
>     // 1. Parent에 대해 context prefix 생성 (LLM 1회 호출)
>     String contextPrefix = contextualEnricher.generateContext(documentText, parent.getContent());
>     parent.setContextPrefix(contextPrefix);
>
>     // 2. Child 분할 후, Parent의 context를 상속
>     List<DocumentChunkJpaEntity> children = splitIntoChildren(parent, 400);
>     for (DocumentChunkJpaEntity child : children) {
>         child.setParentChunkId(parent.getId());
>         child.setContextPrefix(contextPrefix);  // Parent context 상속
>         child.setEnrichedContent(contextPrefix + "\n" + child.getContent());
>     }
>
>     // 3. Child만 벡터 인덱싱
>     vectorize(children);
> }
> ```

> **⚠️ Evidence 예산 확대 필요 (리뷰 #13)**
>
> 현재 Evidence 예산은 8,000자(`OpenAiComposeStep:26`)인데, Parent 청크(1,500자) + context prefix(~200자) = **~1,700자/건**이므로 Top-5 결과만으로 8,500자를 초과한다.
>
> **변경**: Evidence 예산을 `12,000자`로 확대 (또는 `gpt-4o`의 128K 컨텍스트를 활용하여 `16,000자`까지 확장 가능)
>
> ```java
> // OpenAiComposeStep.java
> private static final int EVIDENCE_BUDGET = 12_000;  // 기존 8,000 → 12,000
> ```

---

#### TASK 3-3: Contextual BM25 인덱싱

**개념**: BM25 키워드 검색에서도 Contextual 정보를 활용

**`PostgresKeywordSearchService` 변경**:
- 기존: 청크 `content` 컬럼에 대해 `tsvector` 생성
- 변경: `enriched_content` (context + content) 컬럼에 대해 `tsvector` 생성

**DB 마이그레이션**:
```sql
-- V17__add_enriched_content_column.sql
ALTER TABLE document_chunks ADD COLUMN enriched_content TEXT;
ALTER TABLE document_chunks ADD COLUMN context_prefix TEXT;

-- tsvector 업데이트
CREATE OR REPLACE FUNCTION update_chunk_tsvector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.tsv = to_tsvector('simple', COALESCE(NEW.enriched_content, NEW.content));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

---

#### TASK 3-4: 이미지 분석 결과의 청킹 및 인덱싱

**개념**: Sprint 1에서 VLM으로 분석한 이미지 결과를 청크로 저장하여 검색 가능하게 만듦

```java
// ImageAnalysisService의 분석 결과를 텍스트로 변환 후 청킹
String imageText = String.format("""
    [이미지 분석: %s]
    유형: %s
    추출 텍스트: %s
    시각적 설명: %s
    기술 컨텍스트: %s
    """,
    fileName, result.imageType(), result.extractedText(),
    result.visualDescription(), result.technicalContext()
);

// 이 텍스트를 document_chunks에 저장하여 벡터 검색 가능하게
chunkingService.chunkAndStore(documentId, imageText, "INQUIRY", inquiryId, fileName);
```

---

### Sprint 3 산출물

| 산출물 | 파일 | 유형 |
|--------|------|------|
| Contextual Chunk Enricher | `ContextualChunkEnricher.java` | 신규 |
| Parent-Child 이중 청킹 | `ChunkingService.java` | 수정 |
| Contextual BM25 | `PostgresKeywordSearchService.java` | 수정 |
| 이미지 인덱싱 | `DocumentIndexingWorker.java` | 수정 |
| DB 마이그레이션 | `V16, V17` | 신규 |

### Sprint 3 검증 기준

- [ ] Contextual 청크가 제품명/섹션명을 포함하는지 확인
- [ ] Parent-Child 검색에서 Child 매칭 → Parent 반환 정상 동작
- [ ] BM25 검색에서 context prefix 용어로도 검색 가능 확인
- [ ] 이미지 분석 결과가 벡터 검색으로 조회 가능 확인
- [ ] 인덱싱 비용 측정 (LLM 호출 횟수, 소요 시간)

---

### Sprint 4: Agentic RAG — 적응형 검색 + 멀티홉 추론 (2주)

> **목표**: 검색 결과가 부족할 때 자동으로 재시도하고, 복합 질문에 다중 문서를 교차 추론
> **예상 효과**: 환각 -30%, 복합 질문 정확도 +20%

#### TASK 4-1: 적응형 검색 루프 (Adaptive Retrieval)

**신규 파일**: `AdaptiveRetrievalAgent.java`

```java
@Service
public class AdaptiveRetrievalAgent {

    private static final int MAX_RETRIES = 3;
    private static final double MIN_CONFIDENCE = 0.50;

    /**
     * ReAct 패턴 기반 적응형 검색.
     *
     * 루프:
     *   1. Reason: 현재 검색 결과의 품질 평가
     *   2. Act: 품질 부족 시 쿼리 재구성 후 재검색
     *   3. Observe: 새 결과 평가
     *   4. 반복 (최대 3회) 또는 "I Don't Know" 반환
     */
    public AdaptiveResult retrieve(String question, String productContext, UUID inquiryId) {
        String currentQuery = question;
        List<RerankResult> bestResults = List.of();
        double bestScore = 0.0;

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            // Act: 검색 실행
            List<RerankResult> results = searchPipeline.search(currentQuery, inquiryId);

            // Observe: 결과 품질 평가
            double topScore = results.isEmpty() ? 0.0 :
                results.stream().mapToDouble(RerankResult::rerankScore).max().orElse(0.0);

            if (topScore > bestScore) {
                bestScore = topScore;
                bestResults = results;
            }

            if (topScore >= MIN_CONFIDENCE) {
                return AdaptiveResult.success(bestResults, attempt + 1);
            }

            // Reason: 왜 실패했는지 분석하고 쿼리 재구성
            currentQuery = reformulateQuery(question, currentQuery, results, attempt);
        }

        // 3회 실패: 최선의 결과 반환 + 낮은 신뢰도 플래그
        return bestResults.isEmpty()
            ? AdaptiveResult.noEvidence(question)
            : AdaptiveResult.lowConfidence(bestResults, bestScore);
    }

    /**
     * 쿼리 재구성 전략 (시도 횟수별):
     *   1차: 동의어/관련어 확장 ("restriction enzyme" → "RE digestion, DNA pretreatment")
     *   2차: 상위 개념으로 확장 ("primer concentration" → "reaction setup conditions")
     *   3차: 영어/한국어 교차 검색
     */
    private String reformulateQuery(String original, String current,
                                     List<RerankResult> results, int attempt) { ... }

    public record AdaptiveResult(
        List<RerankResult> evidences,
        int attempts,
        double confidence,
        ResultStatus status           // SUCCESS, LOW_CONFIDENCE, NO_EVIDENCE
    ) {
        enum ResultStatus { SUCCESS, LOW_CONFIDENCE, NO_EVIDENCE }
    }
}
```

---

#### TASK 4-2: Tool-Calling 기반 검색 에이전트

**개념**: LLM이 어떤 검색 도구를 쓸지 직접 결정

**신규 파일**: `SearchToolAgent.java`

```java
@Service
public class SearchToolAgent {

    /**
     * OpenAI Function Calling으로 검색 전략을 LLM이 결정.
     *
     * 사용 가능한 도구:
     *   - search_by_product(productName, query): 제품별 특화 검색
     *   - search_inquiry_docs(inquiryId, query): 첨부 문서 내 검색
     *   - search_knowledge_base(query, category): KB 카테고리별 검색
     *   - get_document_section(docId, pageRange): 특정 페이지 직접 조회
     *   - search_similar_inquiries(query): 유사 과거 문의 검색
     */
    public List<RerankResult> agenticSearch(String question, UUID inquiryId) {
        List<ToolDefinition> tools = List.of(
            searchByProductTool(),
            searchInquiryDocsTool(),
            searchKnowledgeBaseTool(),
            getDocumentSectionTool(),
            searchSimilarInquiriesTool()
        );

        // LLM이 질문을 분석하고 적절한 도구를 선택/조합
        List<ToolCall> toolCalls = openAiClient.chatWithTools(
            buildSystemPrompt(), question, tools
        );

        // 도구 실행 및 결과 병합
        return executeAndMerge(toolCalls);
    }
}
```

---

#### TASK 4-3: 다중 문서 교차 추론 (Cross-Document Reasoning)

**개념**: 복합 질문에서 여러 문서의 정보를 연결

```java
/**
 * Multi-hop 검색: 1차 검색 결과에서 추가 검색이 필요한 엔티티를 추출하여 2차 검색.
 *
 * 예시:
 *   Q: "QX700에서 naica 시약 사용 가능한가요?"
 *   Step 1: QX700 compatible reagents 검색 → "ddPCR Supermix 권장"
 *   Step 2: naica 매뉴얼에서 "ddPCR Supermix" 호환성 검색
 *   Step 3: 두 결과를 교차 검증하여 최종 답변
 */
public class MultiHopRetriever {

    public MultiHopResult retrieve(String question, UUID inquiryId) {
        // Step 1: 초기 검색
        List<RerankResult> hop1Results = adaptiveAgent.retrieve(question, ...);

        // Step 2: LLM이 추가 검색 필요 여부 판단
        HopDecision decision = evaluateNeedForNextHop(question, hop1Results);

        if (decision.needsMoreHops()) {
            // Step 3: 추출된 엔티티로 2차 검색
            List<RerankResult> hop2Results = adaptiveAgent.retrieve(
                decision.nextQuery(), ...);

            // Step 4: 결과 병합 및 교차 검증
            return mergeAndVerify(hop1Results, hop2Results);
        }

        return MultiHopResult.singleHop(hop1Results);
    }
}
```

---

#### TASK 4-4: Critic Agent — 답변 교차 검증

**신규 파일**: `CriticAgentService.java`

```java
@Service
public class CriticAgentService {

    /**
     * Composer가 생성한 답변을 별도 LLM 호출로 교차 검증.
     *
     * 검증 항목:
     *   1. 답변의 각 주장이 evidence에 실제로 존재하는가? (Faithfulness)
     *   2. 인용된 페이지 번호가 정확한가? (Citation Accuracy)
     *   3. 수치/절차가 evidence와 일치하는가? (Factual Consistency)
     *   4. 질문에 대한 답변이 완전한가? (Completeness)
     */
    public CriticResult critique(String draft, String question,
                                  List<EvidenceItem> evidences) {
        String prompt = String.format("""
            당신은 Bio-Rad 기술 문서 사실 검증 전문가입니다.

            ## 원본 질문
            %s

            ## 생성된 답변 초안
            %s

            ## 참조 근거 자료
            %s

            ## 검증 요청
            답변의 각 문장에 대해:
            1. 이 주장이 근거 자료에 존재하는가? (TRUE/FALSE)
            2. 인용이 정확한가? (CORRECT/INCORRECT/MISSING)
            3. 수치가 일치하는가? (MATCH/MISMATCH/NOT_APPLICABLE)

            문제가 발견되면 구체적인 수정 제안을 포함하세요.
            """, question, draft, formatEvidences(evidences));

        return parseCriticResponse(openAiClient.chat(prompt));
    }

    public record CriticResult(
        double faithfulnessScore,        // 0.0 - 1.0
        List<ClaimVerification> claims,  // 주장별 검증 결과
        List<String> corrections,        // 수정 제안
        boolean needsRevision            // 재작성 필요 여부
    ) {}
}
```

**파이프라인 통합**:
```
Compose → CriticAgent → 문제 발견 시 Compose 재호출 (최대 1회) → SelfReview
```

---

### Sprint 4 산출물

| 산출물 | 파일 | 유형 |
|--------|------|------|
| 적응형 검색 루프 | `AdaptiveRetrievalAgent.java` | 신규 |
| Tool-Calling 검색 에이전트 | `SearchToolAgent.java` | 신규 |
| Multi-Hop 검색 | `MultiHopRetriever.java` | 신규 |
| Critic Agent | `CriticAgentService.java` | 신규 |

### Sprint 4 — Quantum Jump 기존 서비스 통합 (리뷰 #9)

> **⚠️ 중요**: Sprint 4의 Agentic RAG 서비스들은 기존 `PRD_RAG_Pipeline_Quantum_Jump.md`에서 구현된 서비스들과 **기능이 중복/충돌**할 수 있다. 통합 방안을 명확히 정의한다.

| 기존 서비스 (Quantum Jump) | 신규 서비스 (본 PRD) | 관계 | 통합 전략 |
|--------------------------|-------------------|------|---------|
| `QuestionDecomposerService` (질문 분해) | `MultiHopRetriever` (다중 문서 교차 추론) | **보완** | Decomposer가 먼저 질문 분해 → 각 서브질문을 MultiHop이 처리. Decomposer는 "분해" 전담, MultiHop은 "교차 검색" 전담 |
| `ProductExtractorService` (제품명 추출) | `SearchToolAgent` (Tool-Calling 검색) | **흡수** | ProductExtractor를 SearchToolAgent의 도구 중 하나로 등록. `search_by_product()` 도구 내부에서 ProductExtractor 호출 |
| 3-Level Fallback Search (KB→문의→전체) | `AdaptiveRetrievalAgent` (적응형 재검색) | **대체** | AdaptiveAgent가 3-Level Fallback을 포함하는 상위 개념. 1차 시도에서 3-Level 사용, 실패 시 쿼리 재구성 후 재시도 |
| `SelfReviewStep` (답변 자체 검토) | `CriticAgentService` (교차 검증) | **체이닝** | Compose → **CriticAgent** (팩트 검증) → **SelfReview** (톤/형식 검토) → 최종 답변. 역할 분리: Critic=팩트, SelfReview=톤 |
| I Don't Know 경로 | `AdaptiveResult.NO_EVIDENCE` | **통합** | AdaptiveAgent가 3회 재시도 후 NO_EVIDENCE → 기존 I Don't Know 경로로 연결 |

**통합 후 파이프라인**:
```
질문 → QuestionDecomposer (분해)
       → 서브질문별: AdaptiveRetrievalAgent (3-Level + 재시도)
         → MultiHopRetriever (교차 추론, 필요 시)
         → RerankingService (리랭킹)
       → Compose (답변 생성)
       → CriticAgent (팩트 검증)
       → SelfReview (톤/형식)
       → Human-in-the-Loop
```

### Sprint 4 검증 기준

- [ ] 적응형 검색: 1차 검색 실패 시 재시도로 정확한 결과 찾는 케이스 확인
- [ ] Tool-Calling: LLM이 질문에 따라 적절한 도구를 선택하는지 확인
- [ ] Multi-Hop: 교차 문서 질문 (예: "QX700 + naica 호환성")에 정확히 답변
- [ ] Critic Agent: 잘못된 인용/수치를 감지하고 수정 제안하는지 확인
- [ ] Quantum Jump 기존 서비스(QuestionDecomposer, ProductExtractor, SelfReview)와 정상 연동 확인
- [ ] I Don't Know 경로가 AdaptiveAgent 3회 실패 후 정상 작동

---

### Sprint 5: 평가 프레임워크 + 프로덕션 안정화 (2주)

> **목표**: 파이프라인 품질을 정량적으로 측정하고, 회귀 방지 체계 구축
> **예상 효과**: 지속적 품질 개선 가능, 배포 전 자동 검증

#### TASK 5-1: RAGAS 기반 평가 파이프라인

**신규 모듈**: `evaluation/`

```
backend/app-api/src/test/java/com/biorad/csrag/evaluation/
├── RagasEvaluator.java         — RAGAS 메트릭 계산 (Java 구현)
├── GoldenDataset.java          — 전문가 정답 데이터셋 로더
├── EvaluationReport.java       — 평가 결과 리포트 생성
└── RagRegressionTest.java      — CI/CD 회귀 테스트
```

**핵심 메트릭**:

| 메트릭 | 측정 대상 | 계산 방법 |
|--------|----------|---------|
| **Faithfulness** | 답변이 evidence에 근거하는지 | 답변의 각 주장을 evidence와 대조 |
| **Answer Relevancy** | 답변이 질문에 관련되는지 | 답변에서 역 질문 생성 후 원 질문과 유사도 |
| **Context Precision** | 상위 검색 결과의 관련성 | 관련 문서가 상위에 랭킹되는 비율 |
| **Context Recall** | 필요한 정보가 검색되었는지 | 정답에 필요한 모든 사실이 검색 결과에 포함된 비율 |

**Golden Dataset 구조**:
```json
{
  "testCases": [
    {
      "id": "TC-001",
      "question": "naica 10x multiplex ddPCR mix 사용 시 gDNA에 restriction enzyme 처리가 필요한가요?",
      "expectedAnswer": "gDNA 사용 시 restriction enzyme을 mixture 제작 전에 처리 권장 (naica 10x multiplex ddpcr mix.pdf, p.6)",
      "relevantDocuments": ["naica_10x_multiplex_ddpcr_mix.pdf"],
      "relevantPages": [6],
      "groundTruthFacts": [
        "restriction enzyme 처리 권장",
        "mixture 제작 전에 처리",
        "페이지 6에 해당 내용 존재"
      ]
    }
  ]
}
```

---

#### TASK 5-2: CI/CD 품질 게이트

**통합 위치**: `./gradlew build` 또는 별도 `./gradlew ragEvaluation`

```java
@Test
void ragPipelineQualityGate() {
    EvaluationReport report = ragasEvaluator.evaluate(goldenDataset);

    assertThat(report.faithfulness()).isGreaterThanOrEqualTo(0.80);
    assertThat(report.answerRelevancy()).isGreaterThanOrEqualTo(0.80);
    assertThat(report.contextPrecision()).isGreaterThanOrEqualTo(0.75);
    assertThat(report.contextRecall()).isGreaterThanOrEqualTo(0.85);
}
```

---

#### TASK 5-3: 프롬프트 버전 관리 시스템

**신규 파일**: `PromptRegistry.java`

```java
@Service
public class PromptRegistry {

    /**
     * 시스템 프롬프트를 코드에서 분리하여 외부 관리.
     * A/B 테스트, 버전 이력, 롤백 지원.
     */
    public String getPrompt(String promptName, String version) {
        // DB 또는 파일 기반 프롬프트 저장소에서 조회
    }

    // 프롬프트 버전:
    // compose-system-v1: 현재 하드코딩된 300줄 프롬프트
    // compose-system-v2: CoT + 인용 강화 프롬프트
    // critic-system-v1: Critic Agent 프롬프트
}
```

---

#### TASK 5-4: 멀티모달 분석 고도화 — VLM 기반 그래프/차트 해석

Sprint 1에서 구축한 이미지 분석을 고도화:

**Amplification Curve 전문 분석**:
```
입력: CFX Maestro amplification curve 스크린샷

분석 결과:
- 감지된 타겟: FAM (6개 웰), HEX (6개 웰)
- FAM 커브 패턴: 5개 웰 정상 증폭 (Ct 15-20), 1개 웰 Flat (no amplification)
- HEX 커브 패턴: 6개 웰 모두 Flat
- RFU 범위: 0 - 4000
- 진단: HEX 채널 타겟 증폭 실패, FAM 채널 1개 웰 증폭 실패
- 추천 확인사항: HEX primer/probe 농도 확인, 타겟 DNA 존재 확인
```

**에러 스크린샷 전문 분석**:
```
입력: CFX Maestro 에러 다이얼로그 스크린샷

분석 결과:
- 소프트웨어: Bio-Rad CFX Maestro
- 에러 메시지: "Method not found: 'Void BioRad.WinCE.PCR.RemoteCommands.AlphaId..ctor(Int32, Boolean)'"
- 에러 유형: .NET 런타임 메서드 누락 (소프트웨어 버전 호환성 문제)
- 추가 경고: "No wells designated as Sample"
- 추천 검색 쿼리: "CFX Maestro AlphaId method not found error" + "CFX Maestro software update"
```

---

#### TASK 5-5: 운영 대시보드 메트릭 확장

**파일**: `OpsMetricsController.java` 수정

```java
// 추가 메트릭:
- 검색 평균 점수 (시간별 추이)
- 리랭킹 전/후 점수 차이
- 적응형 검색 재시도 비율
- HyDE 사용 비율
- 이미지 문의 비율
- Critic Agent 수정 비율
- 평균 답변 Faithfulness 점수
```

---

### Sprint 5 산출물

| 산출물 | 파일 | 유형 |
|--------|------|------|
| RAGAS 평가 프레임워크 | `evaluation/` 패키지 | 신규 |
| Golden Dataset | `test/resources/golden-dataset.json` | 신규 |
| CI/CD 품질 게이트 | `RagRegressionTest.java` | 신규 |
| 프롬프트 버전 관리 | `PromptRegistry.java` | 신규 |
| 멀티모달 분석 고도화 | `ImageAnalysisService.java` | 수정 |
| 운영 메트릭 확장 | `OpsMetricsController.java` | 수정 |

### Sprint 5 검증 기준

- [ ] Golden Dataset 20개 이상 케이스 구축
- [ ] RAGAS 4개 메트릭 모두 0.75 이상 달성
- [ ] CI/CD에서 메트릭 하락 시 빌드 실패 확인
- [ ] 프롬프트 버전 변경이 코드 배포 없이 가능한지 확인
- [ ] 이미지 분석 결과가 답변 품질에 기여하는지 정성 평가

---

## 5. 전체 Sprint 로드맵 요약

```
Sprint 1 (Week 1-2)                Sprint 2 (Week 3-4)
┌─────────────────────┐            ┌─────────────────────┐
│ 전처리 Critical 수정  │            │ 임베딩 업그레이드     │
│ ─────────────────── │            │ ─────────────────── │
│ • cleanText() 수정   │            │ • 3-large 전환       │
│ • 문장 분리 수정      │ ────────→  │ • HyDE 적용          │
│ • 테이블 추출        │            │ • 리랭킹 도입         │
│ • 이미지 업로드/분석   │            │ • 비대칭 임베딩       │
│ • 헤더/푸터 제거      │            │ • Verdict 가중점수    │
└─────────────────────┘            └─────────────────────┘
                                            │
Sprint 3 (Week 5-6)                         │
┌─────────────────────┐                     │
│ Contextual Retrieval │                     │
│ ─────────────────── │ ←───────────────────┘
│ • 청크 문맥 주입      │
│ • Parent-Child 청킹  │
│ • Contextual BM25   │
│ • 이미지 인덱싱       │
└────────┬────────────┘
         │
Sprint 4 (Week 7-8)               Sprint 5 (Week 9-10)
┌─────────────────────┐            ┌─────────────────────┐
│ Agentic RAG         │            │ 평가 + 안정화         │
│ ─────────────────── │            │ ─────────────────── │
│ • 적응형 검색 루프    │            │ • RAGAS 프레임워크    │
│ • Tool-Calling 에이전트│ ────────→ │ • Golden Dataset     │
│ • Multi-Hop 추론     │            │ • CI/CD 품질 게이트   │
│ • Critic Agent       │            │ • 프롬프트 버전 관리   │
└─────────────────────┘            │ • 멀티모달 고도화     │
                                   └─────────────────────┘
```

---

## 6. 비용 분석

### API 호출 비용 증가 (월간 추정, 문의 100건/월 기준)

| 항목 | 현재 | Sprint 5 이후 (gpt-4o-mini) | Sprint 5 이후 (gpt-4o) | 비고 |
|------|------|---------------------------|----------------------|------|
| **임베딩** | $2 | $13 | $13 | 3-large, 차원 2배→비용 ~6배 |
| **HyDE** | $0 | $5 | $5 | 문의당 gpt-4o-mini 1회 (HyDE는 mini로 충분) |
| **Contextual Enrichment** | $0 | **$3** | **$75-225** | 리뷰 #4 반영: mini=$3, 4o=$75-225 |
| **리랭킹** | $0 | $8 | $8 | Cohere Rerank 또는 OpenAI |
| **이미지 분석 (VLM)** | $0 | $10 | $10 | GPT-4o Vision, 이미지 문의 30건 기준 |
| **Critic Agent** | $0 | $12 | $30 | mini=$12, 4o=$30 (정확도 차이 큼) |
| **적응형 재검색** | $0 | $7 | $15 | 평균 1.5회 검색 (30%가 재시도) |
| **합계** | **~$2/월** | **~$58/월** | **~$156-306/월** | |

> **비용 참고**: 현재 OpenAI API 예산 $120/월 기준, `gpt-4o-mini` 중심 운영 시 예산 내 충분. `gpt-4o` 전면 사용 시 예산 상향 필요할 수 있으나, Contextual Enrichment만 `gpt-4o`로 격상해도 대부분의 품질 향상 효과를 얻을 수 있음.

---

## 7. 기술적 리스크 및 완화

| 리스크 | 영향 | 확률 | 완화 방안 |
|--------|------|------|---------|
| Contextual Enrichment LLM 비용 폭증 | 인덱싱 비용 증가 | 중 | gpt-4o-mini 우선 + 배치 처리 + 캐싱. gpt-4o 전환 시 $75-225/월 (리뷰 #4) |
| 리랭킹 지연으로 응답 시간 증가 | UX 저하 | 중 | 비동기 처리, 120ms 타임아웃 설정 |
| 이미지 분석 VLM 비용 | 예산 초과 | 낮 | 이미지 크기 제한 (20MB), 분석 캐싱 |
| Parent-Child 인덱스 용량 증가 | DB 스토리지 | 낮 | 자식 청크만 벡터 저장, 부모는 DB만 |
| 전체 재인덱싱 시간 | 서비스 다운타임 | 중 | Blue-Green 컬렉션 전환 (리뷰 #8, TASK 2-1 참조) |
| 적응형 검색 무한 루프 | 응답 지연 | 낮 | MAX_RETRIES=3 하드 리밋 + 전체 30초 타임아웃 |
| P99 응답 시간 28초+ | UX 저하 | 중 | 병렬 실행 + 스트리밍 진행률 표시 + 단계별 타임아웃 (리뷰 #6) |
| DB 마이그레이션 번호 충돌 | 빌드 실패 | 낮 | V15-V17 번호는 현재 미사용 확인 완료. 병렬 개발 시 Flyway `outOfOrder=true` 설정 (리뷰 #11) |

### 7.1 Mock 모드 폴백 전략 (리뷰 #7)

> **⚠️ 현재 시스템은 `OPENAI_ENABLED=false` 시 `MockEmbeddingService`를 사용하는 Mock 모드를 지원한다. 본 PRD에서 추가하는 모든 LLM/외부 서비스에도 동일한 Mock 폴백이 필요하다.**

| 신규 서비스 | Mock 구현 | 동작 | Sprint |
|-----------|---------|------|--------|
| `ImageAnalysisService` | `MockImageAnalysisService` | 고정 텍스트 반환 ("Mock: 이미지 분석 불가") | S1 |
| `HydeQueryTransformer` | `MockHydeTransformer` | 원본 질문을 그대로 `embedQuery()`에 전달 (HyDE 스킵) | S2 |
| `RerankingService` | `MockRerankingService` | 입력 순서 그대로 반환 (리랭킹 스킵, 원점수 유지) | S2 |
| `ContextualChunkEnricher` | `MockContextualEnricher` | context prefix 없이 원본 content만 반환 | S3 |
| `AdaptiveRetrievalAgent` | `MockAdaptiveAgent` | 1회 검색만 수행, 재시도 없음 | S4 |
| `CriticAgentService` | `MockCriticAgent` | 항상 `needsRevision=false` 반환 | S4 |
| `MultiHopRetriever` | `MockMultiHopRetriever` | 1-hop만 수행, 추가 검색 없음 | S4 |

**구현 패턴** (기존 `MockEmbeddingService`와 동일):
```java
@Service
@ConditionalOnProperty(name = "openai.enabled", havingValue = "false")
public class MockHydeTransformer implements HydeQueryTransformerInterface {
    @Override
    public List<Double> transformAndEmbed(String question, String productContext) {
        // HyDE 스킵: 원본 질문을 직접 임베딩
        return embeddingService.embedQuery(question);
    }
}
```

**원칙**: Mock 모드에서도 전체 파이프라인이 에러 없이 동작해야 하며, `./gradlew build` (H2 + Mock)가 항상 통과해야 한다.

---

## 8. 성공 기준 총괄

| 메트릭 | 현재 | S1 이후 | S2 이후 | S3 이후 | S4 이후 | S5 이후 |
|--------|------|--------|--------|--------|--------|--------|
| Faithfulness | ~35% | 45% | 60% | 70% | 80% | ≥85% |
| Context Precision | ~40% | 55% | 65% | 75% | 82% | ≥85% |
| Context Recall | ~45% | 55% | 70% | 80% | 87% | ≥90% |
| 인용 정확도 | ~25% | 40% | 60% | 70% | 80% | ≥85% |
| 이미지 문의 대응 | 0% | 50% | 50% | 60% | 70% | ≥80% |

#### 응답 시간 상세 추정 (리뷰 #6 반영)

> **⚠️ v1.0에서 "평균 응답 시간 ~16초"로 단일 값만 제시했으나, 실제 운영에서는 분포가 중요하다.**

| Sprint | P50 (중간값) | P95 | P99 (최악) | 주요 추가 지연 요인 |
|--------|------------|-----|-----------|-----------------|
| 현재 | ~6초 | ~10초 | ~15초 | 기본 파이프라인 |
| S1 이후 | ~7초 | ~11초 | ~16초 | +VLM 이미지 분석 (~3초, 이미지 있을 때만) |
| S2 이후 | ~9초 | ~14초 | ~20초 | +HyDE LLM 호출 (~2초) + 리랭킹 (~0.5초) |
| S3 이후 | ~10초 | ~16초 | ~22초 | +Contextual 검색은 인덱싱 시 처리 (쿼리 시간 변화 미미) |
| S4 이후 | ~12초 | ~20초 | ~30초 | +적응형 재시도 (30% 확률, +4-8초) + Critic Agent (~3초) + Multi-Hop (~4초) |
| S5 이후 | ~11초 | ~18초 | ~28초 | 프롬프트 최적화, 병렬 처리로 개선 |

**P99 최악 시나리오 (S4)**: 이미지 분석(3s) + HyDE(2s) + 적응형 3회 재시도(12s) + Multi-Hop 2차(4s) + Compose(3s) + Critic(3s) + SelfReview(1s) = **~28초**

**완화 전략**:
- HyDE와 BM25 검색을 **병렬 실행** (HyDE 결과 대기 중 BM25 먼저 실행)
- 적응형 재시도에 **단계별 타임아웃** 설정 (전체 파이프라인 30초 하드 리밋)
- 프론트엔드에 **스트리밍 진행률 표시** ("검색 중..." → "분석 중..." → "답변 생성 중...")

---

## 9. 참고 자료

- [Anthropic — Contextual Retrieval (2024)](https://www.anthropic.com/news/contextual-retrieval): 검색 실패 67% 감소
- [Jina AI — Late Chunking](https://jina.ai/news/late-chunking-in-long-context-embedding-models/): 문맥 보존 청킹
- [Cross-Encoder Reranking Study](https://app.ailog.fr/en/blog/news/reranking-cross-encoders-study): RAG 정확도 33-40% 향상
- [HyDE — Hypothetical Document Embeddings](https://arxiv.org/abs/2212.10496): 검색 정밀도 +42pp
- [RAGAS Framework](https://docs.ragas.io/): RAG 평가 표준
- [ColPali — Visual Document Retrieval (ICLR 2025)](https://proceedings.iclr.cc/paper_files/paper/2025/file/99e9e141aafc314f76b0ca3dd66898b3-Paper-Conference.pdf)
- [MAIN-RAG (ACL 2025)](https://aclanthology.org/2025.acl-long.131/): 멀티에이전트 RAG
- [Voyage AI v4](https://blog.voyageai.com/2026/01/15/voyage-4/): 비대칭 임베딩
- [Docling — PDF Table Extraction](https://github.com/DS4SD/docling): 97.9% 테이블 정확도
