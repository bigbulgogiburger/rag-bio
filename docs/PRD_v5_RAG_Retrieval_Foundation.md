# PRD v5 — RAG 검색 기반 고도화 (Retrieval Foundation Enhancement)

> **문서 메타데이터**
> - Version: 5.0
> - Date: 2026-02-22
> - Author: CS RAG 팀
> - Status: Draft — Review Required
> - Scope: Sprint 즉시 (1-Sprint, 긴급 품질 개선)
> - 선행 PRD: PRD v2 (서비스 고도화), PRD RAG Pipeline Quantum Jump

---

## 0. Executive Summary

라이브 환경에서 30개 청크를 검색해도 답변 품질이 낮은 근본 원인을 분석한 결과, **검색(Retrieval) 파이프라인 입력/매칭 단계에 6개의 구조적 문제**가 발견되었다. LLM 프롬프트 최적화(증거 배분, Verify 비용 절감 등)는 이미 별도 적용 완료했으나, 이는 검색 단계에서 관련 청크를 제대로 가져오지 못하는 근본 문제를 해결하지 못한다.

본 PRD는 **"Garbage In = Garbage Out"의 핵심 지점인 검색 기반(Retrieval Foundation)**을 개선한다.

**핵심 원칙**: 기존 인프라(PostgreSQL + Qdrant + OpenAI)를 유지하되, 검색 품질을 구조적으로 끌어올린다. Elasticsearch 등 새 인프라 도입 없이, 코드 레벨 개선으로 최대 효과를 추출한다.

---

## 1. 현황 분석 (AS-IS)

### 1.1 검색 파이프라인 현재 구조

```
사용자 질문 (한국어)
  → QueryTranslation (한→영 번역, gpt-4o-mini)
    → 번역된 영어 쿼리만 사용 (원문 한국어 폐기)
      → Embedding (text-embedding-3-small)
        → Qdrant 벡터 검색 (topK×2)
        → PostgreSQL 키워드 검색 (topK×2)
          → RRF Fusion (k=60, 가중치 1:1)
            → topK개 반환 (필터링 없음)
```

### 1.2 발견된 6개 구조적 문제

| ID | 문제 | 영향도 | 원인 |
|----|------|--------|------|
| RF-01 | 한국어 키워드 검색 무효 | 높음 | `'simple'` 사전: 형태소 분석 없음 → "프로토콜을" ≠ "프로토콜" |
| RF-02 | 청크 오버랩 부족 | 중간 | OVERLAP=100자(1-2문장) → 경계에서 맥락 끊김 |
| RF-03 | 청크에 메타데이터 없음 | 중간 | 임베딩이 "어느 매뉴얼의 내용인지" 모름 → 검색 정밀도 저하 |
| RF-04 | SearchFilter.forInquiry() 무효 | 중간 | inquiryId가 Qdrant/PostgreSQL 쿼리에 실제 반영 안 됨 |
| RF-05 | 구조 무시 청킹 | 높음 | 섹션/헤딩/테이블 구조 파괴, 프로토콜 단계 분리 |
| RF-06 | tsvector 인덱싱 미정규화 | 중간 | 청크 내용도 어절 그대로 인덱싱 → 키워드 매칭 실패 |

### 1.3 영향 범위

- **답변 품질**: 검색 단계에서 관련 청크를 못 가져오면 LLM이 아무리 좋아도 답변 불가
- **비용**: 무관한 청크가 LLM 프롬프트에 포함되어 토큰 낭비
- **사용자 경험**: "30개 검색했는데 답변이 이상하다" → 시스템 신뢰도 하락

---

## 2. 목표 (Goals)

### 2.1 비즈니스 목표

| 목표 | 측정 지표 | 현재 | 목표 |
|------|----------|------|------|
| 키워드 검색 한국어 작동 | 한국어 쿼리 키워드 매칭률 | ~10% (조사 불일치) | 70%+ |
| 청크 맥락 보존 | 인접 청크 오버랩 문장 수 | 1-2문장 | 4-6문장 |
| 검색 정밀도 향상 | 상위 10건 중 관련 청크 비율 | 체감 저조 | 개선 (정성적) |
| 문의별 검색 범위 정확화 | 타 문의 문서 혼입 여부 | 필터 무효 | 정확 필터 |

### 2.2 비목표 (Non-Goals)

- Elasticsearch/OpenSearch 등 새 검색 엔진 도입
- 임베딩 모델 변경 (text-embedding-3-small 유지)
- 쿼리 번역 로직 변경 (영어 번역은 유지)
- 프론트엔드 변경 (백엔드 검색 파이프라인만 개선)

---

## 3. 기능 요구사항

### 3.1 [RF-01] 한국어 키워드 검색 정규화

**문제**: `plainto_tsquery('simple', '프로토콜을')` → "프로토콜" 매칭 실패

**해결**: 쿼리와 인덱싱 양쪽에서 한국어 조사/어미를 제거하여 어근 형태로 통일

#### 3.1.1 요구사항

| ID | 요구사항 | 우선순위 |
|----|----------|---------|
| RF-01-01 | PostgresKeywordSearchService에서 쿼리 전처리: 한국어 조사/어미 정규식 제거 | MUST |
| RF-01-02 | 자주 쓰이는 조사 패턴 포함: 을/를/이/가/은/는/에/에서/으로/의/와/과/도/만/까지 | MUST |
| RF-01-03 | 자주 쓰이는 어미 패턴 포함: 합니다/입니다/됩니다/습니다/세요/하여/하고 | MUST |
| RF-01-04 | 영문/숫자/기술 용어는 보존 (ddPCR, CFX96, 20μL 등) | MUST |

#### 3.1.2 기술 구현

```java
// PostgresKeywordSearchService.java에 추가
private static final Pattern KO_SUFFIX = Pattern.compile(
    "(을|를|이|가|은|는|에서|에게|으로|로|의|와|과|도|만|까지|부터|처럼|보다|라고|이라고)(?=\\s|$)"
);
private static final Pattern KO_ENDING = Pattern.compile(
    "(합니다|입니다|됩니다|습니다|했습니다|되었습니다|하세요|하여|하고|해서|한다|된다|인지|인가)(?=[.?!,\\s]|$)"
);

private String normalizeKorean(String query) {
    String result = KO_SUFFIX.matcher(query).replaceAll("");
    result = KO_ENDING.matcher(result).replaceAll("");
    return result.replaceAll("\\s+", " ").trim();
}
```

**재인덱싱**: 불필요 (쿼리 전처리만)
**인수 조건**:
- [ ] "프로토콜을 설정합니다" 검색 시 "프로토콜 설정" 포함 청크 매칭
- [ ] "ddPCR" 같은 영문 기술 용어는 변경 없이 보존
- [ ] 기존 테스트 통과

---

### 3.2 [RF-02] 청크 오버랩 확대 (100 → 300)

**문제**: 인접 청크 경계에서 프로토콜 단계, 인과관계 등 맥락 끊김

**해결**: `OVERLAP_CHARS` 상수를 100에서 300으로 확대

#### 3.2.1 요구사항

| ID | 요구사항 | 우선순위 |
|----|----------|---------|
| RF-02-01 | ChunkingService.OVERLAP_CHARS를 300으로 변경 | MUST |
| RF-02-02 | 기존 청킹 테스트가 새 오버랩에 맞게 업데이트 | MUST |

#### 3.2.2 기술 구현

```java
// ChunkingService.java
private static final int OVERLAP_CHARS = 300; // 100 → 300
```

**재인덱싱**: 필요 (기존 청크 재생성)
**인수 조건**:
- [ ] 새로 청킹된 인접 청크가 ~300자(4-6문장) 겹침 확인
- [ ] 빌드 및 테스트 통과

---

### 3.3 [RF-03] 청크 파일명 프리픽스

**문제**: 임베딩에 문서 출처 정보가 없어 "naica 설정" 검색 시 naica 매뉴얼 청크 우선순위 낮음

**해결**: 청크 내용 앞에 `[파일명]` 프리픽스를 추가하여 임베딩에 문맥 반영

#### 3.3.1 요구사항

| ID | 요구사항 | 우선순위 |
|----|----------|---------|
| RF-03-01 | ChunkingService에 fileName 파라미터를 받는 오버로드 추가 | MUST |
| RF-03-02 | 청크 content 앞에 `[파일명] ` 프리픽스 추가 | MUST |
| RF-03-03 | 기존 fileName 없는 호출은 프리픽스 없이 동작 (하위 호환) | MUST |
| RF-03-04 | DocumentIndexingService, KnowledgeBaseService에서 fileName 전달 | MUST |

#### 3.3.2 기술 구현

```java
// ChunkingService.java - 새 오버로드
public int chunkAndStore(UUID documentId, String text, String sourceType, UUID sourceId, String fileName) {
    String prefixedText = (fileName != null && !fileName.isBlank())
        ? "[" + fileName + "] " + text
        : text;
    return chunkAndStore(documentId, prefixedText, sourceType, sourceId);
}

// 페이지 기반 청킹도 동일 오버로드
public int chunkAndStore(UUID documentId, List<PageText> pageTexts, String sourceType, UUID sourceId, String fileName) {
    // 각 PageText의 text 앞에 프리픽스 추가
}
```

**재인덱싱**: 필요
**인수 조건**:
- [ ] 새로 인덱싱된 청크의 content가 `[파일명] 원본내용...` 형식
- [ ] 벡터 검색에서 파일명 관련 쿼리 시 해당 파일 청크 우선 반환
- [ ] 빌드 및 테스트 통과

---

### 3.4 [RF-04] SearchFilter.forInquiry() 유효화

**문제**: `forInquiry(inquiryId)` → inquiryId가 Qdrant/PostgreSQL에 반영 안 됨 → 전체 DB 검색

**해결**: 답변 생성 시 해당 문의의 문서 ID + KB 문서를 합쳐서 검색 범위 설정

#### 3.4.1 요구사항

| ID | 요구사항 | 우선순위 |
|----|----------|---------|
| RF-04-01 | AnswerOrchestrationService에서 문의의 문서 ID 조회 후 SearchFilter에 반영 | MUST |
| RF-04-02 | KB 문서는 항상 검색 대상에 포함 (sourceType 필터 사용) | MUST |
| RF-04-03 | 제품 필터가 있으면 제품 필터 + 소스 타입 필터 조합 | SHOULD |

#### 3.4.2 기술 구현

SearchFilter.forInquiry()를 호출하는 대신, 해당 문의의 문서 ID를 조회하고 sourceType 필터를 결합:

```java
// AnswerOrchestrationService.run() 에서
Set<UUID> inquiryDocIds = documentRepository.findByInquiryId(inquiryId)
    .stream().map(DocumentMetadataJpaEntity::getId).collect(Collectors.toSet());

SearchFilter filter;
if (product != null) {
    filter = new SearchFilter(inquiryId, inquiryDocIds, product.productFamily(),
                              Set.of("INQUIRY", "KNOWLEDGE_BASE"));
} else {
    filter = new SearchFilter(inquiryId, inquiryDocIds, null,
                              Set.of("INQUIRY", "KNOWLEDGE_BASE"));
}
```

단, documentIds가 설정되면 Qdrant에서 해당 문서의 청크만 검색되므로, KB 문서를 포함하려면:
- 방법 A: documentIds에 KB 문서 ID도 추가 (KB 문서가 많으면 비효율)
- 방법 B: sourceType 필터만 사용하고 documentIds는 사용하지 않음 (범위 넓음)
- **방법 C (권장)**: documentIds 필터 제거, inquiryId와 연결된 문서 검색은 sourceType + productFamily로 충분. forInquiry()의 inquiryId는 감사 로깅용으로만 사용

**재인덱싱**: 불필요
**인수 조건**:
- [ ] 문의 A의 답변 생성 시 문의 B의 첨부 문서 청크가 결과에 포함되지 않음
- [ ] KB 문서 청크는 항상 검색 대상에 포함됨
- [ ] 기존 테스트 통과

---

### 3.5 [RF-05] 섹션/헤딩 인식 청킹

**문제**: 순수 문장 분리 → 기술 문서의 섹션 헤딩이 이전 청크 끝에 붙음

**해결**: 헤딩 패턴 감지 시 새 청크를 시작하는 로직 추가

#### 3.5.1 요구사항

| ID | 요구사항 | 우선순위 |
|----|----------|---------|
| RF-05-01 | 헤딩 패턴 감지: `^\d+\.`, `^Chapter`, `^Section`, `^[A-Z][A-Z\s]+$` | MUST |
| RF-05-02 | 헤딩 감지 시 현재 청크를 종료하고 새 청크 시작 | MUST |
| RF-05-03 | 헤딩이 없는 문서에서는 기존 문장 분리와 동일 동작 | MUST |
| RF-05-04 | 테이블 패턴(탭/다중 공백으로 구분된 행) 감지 시 단일 청크 유지 | SHOULD |

#### 3.5.2 기술 구현

```java
// ChunkingService.java에 추가
private static final Pattern HEADING_PATTERN = Pattern.compile(
    "^(\\d+\\.\\d*\\s|Chapter\\s|Section\\s|CHAPTER\\s|[A-Z][A-Z\\s]{3,}$)",
    Pattern.MULTILINE
);

// splitIntoSentences() 대신 splitIntoSections()으로 1차 분할 후 각 섹션 내에서 문장 분할
```

**재인덱싱**: 필요
**인수 조건**:
- [ ] "3.1 Thermal Profile Setup" 같은 헤딩이 새 청크의 시작에 위치
- [ ] 헤딩 없는 일반 텍스트는 기존과 동일하게 처리
- [ ] 빌드 및 테스트 통과

---

### 3.6 [RF-06] tsvector 한국어 정규화

**문제**: 청크 인덱싱에서도 `to_tsvector('simple', content)` → 어절 그대로 저장

**해결**: PostgreSQL 트리거에서 한국어 조사/어미를 제거한 후 tsvector 생성

#### 3.6.1 요구사항

| ID | 요구사항 | 우선순위 |
|----|----------|---------|
| RF-06-01 | V27 마이그레이션: 트리거 함수에 한국어 정규화 로직 추가 | MUST |
| RF-06-02 | 기존 청크의 tsvector 일괄 업데이트 | MUST |
| RF-06-03 | H2 환경에서는 no-op (PostgreSQL 전용) | MUST |

#### 3.6.2 DB 마이그레이션

```sql
-- V27__korean_tsvector_normalization.sql (PostgreSQL 전용 Java migration)

-- 트리거 함수: 한국어 조사/어미 제거 후 tsvector 생성
CREATE OR REPLACE FUNCTION document_chunks_tsv_trigger() RETURNS trigger AS $$
DECLARE
    normalized TEXT;
BEGIN
    normalized := coalesce(NEW.content, '');
    -- 한국어 조사 제거
    normalized := regexp_replace(normalized, '(을|를|이|가|은|는|에서|에게|으로|로|의|와|과|도|만|까지|부터)(\s|$)', '\2', 'g');
    -- 한국어 어미 제거
    normalized := regexp_replace(normalized, '(합니다|입니다|됩니다|습니다|했습니다|되었습니다|하세요|하여|하고|해서)([.?!,\s]|$)', '\2', 'g');
    NEW.content_tsv := to_tsvector('simple', normalized);
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

-- 기존 데이터 일괄 업데이트 (트리거 재실행)
UPDATE document_chunks SET content = content WHERE content_tsv IS NOT NULL;
```

**재인덱싱**: 마이그레이션에서 자동 처리 (앱 레벨 재인덱싱 불필요, 단 RF-02/03/05와 함께 재인덱싱하면 마이그레이션 불필요)
**인수 조건**:
- [ ] "프로토콜" 키워드 검색 시 "프로토콜을", "프로토콜이" 포함 청크 매칭
- [ ] H2 개발 환경에서 오류 없음
- [ ] 기존 테스트 통과

---

## 4. 실행 계획

### 4.1 Phase 구성

```
Phase 1: 즉시 적용 (재인덱싱 불필요)
├── RF-01: 한국어 키워드 쿼리 정규화
└── RF-04: SearchFilter 유효화

Phase 2: 재인덱싱 묶음 적용
├── RF-02: 오버랩 300 확대
├── RF-03: 파일명 프리픽스
├── RF-05: 헤딩 인식 청킹
├── RF-06: tsvector 한국어 정규화
└── → 전체 재인덱싱 1회 실행
```

### 4.2 병렬 실행 구조

```
[Team Lead]
  ├── [Agent A: keyword-normalizer]  → RF-01 + RF-06 (키워드 검색 양쪽)
  ├── [Agent B: chunk-enhancer]      → RF-02 + RF-03 + RF-05 (청킹 개선 묶음)
  └── [Agent C: filter-fixer]        → RF-04 (SearchFilter 수정)
```

Agent A와 C는 서로 독립. Agent B는 ChunkingService 집중. 병렬 실행 가능.

---

## 5. 리스크 및 대응

| 리스크 | 영향 | 확률 | 대응 |
|--------|------|------|------|
| 한국어 정규식이 기술 용어 훼손 | 검색 품질 저하 | 낮음 | 영문/숫자 패턴은 보존하는 정규식 설계 |
| 헤딩 패턴 오감지 | 불필요한 청크 분할 | 중간 | 최소 300자 이상이어야 분할, fallback은 기존 로직 |
| 재인덱싱 시 서비스 중단 | 검색 불가 | 확실 | 테스트 환경이므로 허용, 재인덱싱 API 기존 존재 |
| SearchFilter 변경 시 검색 범위 축소 | 관련 청크 누락 | 낮음 | sourceType으로 KB 항상 포함 보장 |

---

## 6. 성공 지표 (KPI)

| 지표 | 현재 | 목표 | 측정 방법 |
|------|------|------|----------|
| 한국어 키워드 매칭률 | ~10% | 70%+ | 테스트 쿼리 10개로 매칭 비율 측정 |
| 인접 청크 오버랩 | ~100자 | ~300자 | 청크 경계 샘플 확인 |
| 답변 내 "확인 후 답변" 비율 | 높음 | 감소 | 라이브 답변 품질 관찰 |
| 검색 필터 정확도 | 무효 | 유효 | 문의별 검색 범위 로그 확인 |

---

> **다음 단계**: Phase 1 + Phase 2를 병렬 agent 팀으로 동시 구현 → 빌드 검증 → 라이브 재인덱싱 → 답변 품질 비교 테스트
