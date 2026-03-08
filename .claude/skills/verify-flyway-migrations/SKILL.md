---
name: verify-flyway-migrations
description: Flyway DB 마이그레이션 일관성 검증. 마이그레이션 추가/수정 후 사용.
---

## Purpose

1. **버전 번호 순서** — V1~V{N} 순서가 연속적이고 간격이 없는지 검증
2. **명명 규칙** — `V{N}__{snake_case_description}.sql` 패턴 준수 확인
3. **SQL 호환성** — H2/PostgreSQL 모두 호환되는 SQL 문법 사용 확인
4. **JPA 엔티티 동기화** — 마이그레이션의 컬럼 정의와 JPA @Column 어노테이션 일치 확인
5. **컬럼 타입 일관성** — 프로젝트 표준 타입 규칙 (UUID PK, TIMESTAMP WITH TIME ZONE 등) 준수 확인

## When to Run

- 새 Flyway 마이그레이션 파일 추가 후
- JPA 엔티티의 @Column 정의 변경 후
- 테이블 스키마 관련 버그 수정 후
- ALTER TABLE 마이그레이션 작성 후
- 컬럼 타입 변경 (VARCHAR → TEXT 등) 후

## Related Files

| File | Purpose |
|------|---------|
| `backend/app-api/src/main/resources/db/migration/V1__init_inquiry_and_documents.sql` | 초기 스키마 (inquiries, documents) |
| `backend/app-api/src/main/resources/db/migration/V4__document_chunks.sql` | document_chunks 테이블 |
| `backend/app-api/src/main/resources/db/migration/V14__knowledge_base.sql` | KB 테이블 + source_type/source_id |
| `backend/app-api/src/main/resources/db/migration/V16__drop_chunks_document_fk.sql` | FK 제거 |
| `backend/app-api/src/main/resources/db/migration/V17__chunk_content_to_text.sql` | content VARCHAR→TEXT |
| `backend/app-api/src/main/resources/db/migration/V18__chunk_page_tracking.sql` | page_start/page_end 컬럼 추가 |
| `backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/chunk/DocumentChunkJpaEntity.java` | 청크 엔티티 |
| `backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/knowledge/KnowledgeDocumentJpaEntity.java` | KB 문서 엔티티 |
| `backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/answer/AnswerDraftJpaEntity.java` | 답변 초안 엔티티 (draft/citations TEXT) |
| `backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/answer/AiReviewResultJpaEntity.java` | AI 리뷰 결과 엔티티 |
| `backend/app-api/src/main/resources/db/migration/V19__inquiry_preferred_tone.sql` | preferred_tone 컬럼 추가 |
| `backend/app-api/src/main/resources/db/migration/V20__ai_workflow_columns.sql` | AI 워크플로우 컬럼 (review/approval) |
| `backend/app-api/src/main/resources/db/migration/V21__answer_draft_text_columns.sql` | draft/citations VARCHAR→TEXT 변환 |
| `backend/app-api/src/main/resources/db/migration/V22__users_and_roles.sql` | 사용자/역할 테이블 |
| `backend/app-api/src/main/resources/db/migration/V23__hybrid_search_tsvector.sql` | tsvector 하이브리드 검색 |
| `backend/app-api/src/main/resources/db/migration/V24__answer_draft_refinement.sql` | 답변 초안 보완 컬럼 |
| `backend/app-api/src/main/resources/db/migration/V25__workflow_run_count.sql` | workflow_run_count 컬럼 추가 |
| `backend/app-api/src/main/resources/db/migration/V26__chunk_product_family.sql` | product_family 컬럼 + 인덱스 + answer_drafts 확장 |
| `backend/app-api/src/main/java/db/migration/V27__KoreanTsvectorNormalization.java` | Java 기반 마이그레이션 (한국어 tsvector 정규화, PostgreSQL 전용 / H2 no-op) |
| `backend/app-api/src/main/resources/db/migration/V28__add_image_analysis_columns.sql` | documents 테이블에 이미지 분석 컬럼 추가 |
| `backend/app-api/src/main/resources/db/migration/V29__parent_child_chunks.sql` | Parent-Child 청킹 컬럼 (parent_chunk_id, chunk_level) |
| `backend/app-api/src/main/resources/db/migration/V30__enriched_content.sql` | 컨텍스트 보강 컬럼 (context_prefix, enriched_content) |
| `backend/app-api/src/main/resources/db/migration/V31__rag_metrics_tracking.sql` | RAG 메트릭 추적 테이블 |
| `backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/document/DocumentMetadataJpaEntity.java` | 문서 메타데이터 엔티티 (이미지 분석 컬럼 포함) |
| `backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/metrics/RagPipelineMetricEntity.java` | RAG 메트릭 엔티티 |
| `backend/app-api/src/main/resources/db/migration/V32__fix_timestamp_tz_and_answer_draft_sync.sql` | V12 TIMESTAMP TZ 보정 |
| `backend/app-api/src/main/resources/db/migration/V33__add_answer_draft_sub_question_columns.sql` | answer_drafts 서브 질문 컬럼 추가 (sub_question_count, decomposed_questions) |

## Workflow

### Step 1: 버전 번호 연속성 확인

**파일:** `backend/app-api/src/main/resources/db/migration/`

**검사:** V1부터 V{최대}까지 빈 번호 없이 연속적인지 확인.

```bash
ls backend/app-api/src/main/resources/db/migration/V*.sql | sed 's/.*\/V\([0-9]*\)__.*/\1/' | sort -n
```

**PASS:** 1, 2, 3, ... N 연속
**FAIL:** 번호 간격 존재 (예: V1, V2, V4 — V3 누락)

### Step 2: 파일 명명 규칙 확인

**파일:** `backend/app-api/src/main/resources/db/migration/`

**검사:** 모든 마이그레이션이 `V{N}__{snake_case}.sql` 패턴을 따르는지 확인.

```bash
ls backend/app-api/src/main/resources/db/migration/ | grep -v '^V[0-9]\+__[a-z_]*\.sql$'
```

**PASS:** 패턴 불일치 파일 없음
**FAIL:** camelCase, 공백, 특수문자 포함 파일 존재

### Step 3: Primary Key 타입 일관성

**검사:** 모든 테이블이 UUID PRIMARY KEY를 사용하는지 확인.

```bash
grep -n "PRIMARY KEY" backend/app-api/src/main/resources/db/migration/V*__*.sql
```

**PASS:** 모든 PK가 `UUID PRIMARY KEY`
**FAIL:** BIGINT, SERIAL 등 다른 타입 사용

### Step 4: Timestamp 타입 일관성

**검사:** 모든 시간 컬럼이 `TIMESTAMP WITH TIME ZONE`을 사용하는지 확인.

```bash
grep -in "TIMESTAMP" backend/app-api/src/main/resources/db/migration/V*__*.sql
```

**PASS:** 모든 TIMESTAMP 컬럼이 `WITH TIME ZONE` 포함
**FAIL:** `TIMESTAMP` 단독 사용 (timezone 정보 누락)

### Step 5: JPA 엔티티 TEXT 컬럼 동기화

**파일:** `DocumentChunkJpaEntity.java`, `KnowledgeDocumentJpaEntity.java`, `AnswerDraftJpaEntity.java`

**검사:** SQL에서 TEXT 타입인 컬럼이 JPA에서도 `columnDefinition = "TEXT"`로 선언되었는지 확인. V17(chunk content), V21(draft/citations) 등 모든 TEXT 변환 마이그레이션을 검사.

```bash
grep -n "columnDefinition.*TEXT\|TYPE TEXT" backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/chunk/DocumentChunkJpaEntity.java backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/knowledge/KnowledgeDocumentJpaEntity.java backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/answer/AnswerDraftJpaEntity.java backend/app-api/src/main/resources/db/migration/V17__chunk_content_to_text.sql backend/app-api/src/main/resources/db/migration/V21__answer_draft_text_columns.sql
```

**PASS:** SQL TEXT 타입과 JPA columnDefinition = "TEXT" 일치 (chunk content, draft, citations 모두)
**FAIL:** SQL은 TEXT인데 JPA에서 length = 4000/5000 등 VARCHAR 설정

### Step 6: VARCHAR 길이 일치 확인

**검사:** JPA @Column(length=N)과 SQL VARCHAR(N)이 일치하는지 확인.

```bash
grep -n "VARCHAR\|length\s*=" backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/knowledge/KnowledgeDocumentJpaEntity.java
```

**PASS:** SQL VARCHAR(500)과 JPA length = 500 등 모든 길이 일치
**FAIL:** 길이 불일치 (예: SQL VARCHAR(100) vs JPA length = 200)

### Step 7: IF NOT EXISTS 사용 확인

**검사:** CREATE TABLE/INDEX에 IF NOT EXISTS가 사용되는지 확인 (멱등성).

```bash
grep -n "CREATE TABLE\|CREATE INDEX\|CREATE UNIQUE INDEX" backend/app-api/src/main/resources/db/migration/V*__*.sql | grep -v "IF NOT EXISTS"
```

**PASS:** 모든 CREATE 구문에 IF NOT EXISTS 포함 (또는 Flyway 버전 관리로 충분)
**FAIL:** IF NOT EXISTS 없이 CREATE 사용 시 재실행 불가 위험

### Step 8: 인덱스 전략 확인

**검사:** 필터링에 사용되는 컬럼에 적절한 인덱스가 있는지 확인.

```bash
grep -n "CREATE.*INDEX" backend/app-api/src/main/resources/db/migration/V*__*.sql
```

**PASS:** status, source_type+source_id, inquiry_id 등 주요 필터 컬럼에 인덱스 존재
**FAIL:** 조회 빈도 높은 컬럼에 인덱스 누락

## Output Format

| # | 검사 항목 | 결과 | 상세 |
|---|----------|------|------|
| 1 | 버전 번호 연속성 | PASS/FAIL | 누락 번호 목록 |
| 2 | 명명 규칙 | PASS/FAIL | 불일치 파일 목록 |
| 3 | PK 타입 일관성 | PASS/FAIL | 비-UUID PK 목록 |
| 4 | Timestamp 타입 | PASS/FAIL | TZ 누락 컬럼 목록 |
| 5 | TEXT 컬럼 동기화 | PASS/FAIL | 불일치 컬럼 목록 |
| 6 | VARCHAR 길이 일치 | PASS/FAIL | 불일치 항목 |
| 7 | IF NOT EXISTS | PASS/FAIL | 미사용 구문 목록 |
| 8 | 인덱스 전략 | PASS/FAIL | 누락 인덱스 제안 |
| 9 | page_start/page_end 동기화 | PASS/FAIL | INT vs Integer 일치 |
| 10 | AI 워크플로우 컬럼 동기화 | PASS/FAIL | V20 컬럼 매핑 |
| 11 | preferred_tone 동기화 | PASS/FAIL | V19 컬럼 매핑 |
| 12 | workflow_run_count 동기화 | PASS/FAIL | V25 컬럼 매핑 |

### Step 9: page_start/page_end JPA 동기화 확인

**파일:** `V18__chunk_page_tracking.sql`, `DocumentChunkJpaEntity.java`

**검사:** V18 마이그레이션의 `page_start`, `page_end` INT 컬럼이 JPA에서 `Integer` 타입으로 선언되고 `@Column(name = "page_start")` / `@Column(name = "page_end")`로 매핑되는지 확인.

```bash
grep -n "page_start\|page_end\|pageStart\|pageEnd" backend/app-api/src/main/resources/db/migration/V18__chunk_page_tracking.sql backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/chunk/DocumentChunkJpaEntity.java
```

**PASS:** SQL INT 컬럼과 JPA Integer 필드가 일치하고, nullable
**FAIL:** 타입 불일치 또는 @Column name 미지정

### Step 10: AI 워크플로우 컬럼 동기화 확인

**파일:** `V20__ai_workflow_columns.sql`, `AnswerDraftJpaEntity.java`, `AiReviewResultJpaEntity.java`

**검사:** V20에서 추가된 AI 워크플로우 컬럼(ai_review_decision, ai_review_score, ai_approval_decision 등)이 JPA 엔티티에 올바르게 매핑되는지 확인.

```bash
grep -n "ai_review\|ai_approval\|aiReview\|aiApproval" backend/app-api/src/main/resources/db/migration/V20__ai_workflow_columns.sql backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/answer/AnswerDraftJpaEntity.java backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/answer/AiReviewResultJpaEntity.java
```

**PASS:** SQL 컬럼과 JPA 필드가 일치 (이름, 타입)
**FAIL:** SQL에 컬럼이 있지만 JPA에 대응 필드 없음 또는 타입 불일치

### Step 11: preferred_tone 컬럼 동기화 확인

**파일:** `V19__inquiry_preferred_tone.sql`, Inquiry 관련 JPA 엔티티

**검사:** V19에서 추가된 preferred_tone VARCHAR 컬럼이 JPA 엔티티에 매핑되는지 확인.

```bash
grep -rn "preferred_tone\|preferredTone" backend/app-api/src/main/resources/db/migration/V19__inquiry_preferred_tone.sql backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/inquiry/
```

**PASS:** SQL VARCHAR 컬럼과 JPA String 필드 일치
**FAIL:** JPA에 preferredTone 필드 없음

### Step 12: workflow_run_count JPA 동기화 확인

**파일:** `V25__workflow_run_count.sql`, `AnswerDraftJpaEntity.java`

**검사:** V25 마이그레이션의 `workflow_run_count INT NOT NULL DEFAULT 0` 컬럼이 JPA에서 `workflowRunCount` int/Integer 필드로 매핑되는지 확인.

```bash
grep -n "workflow_run_count\|workflowRunCount" backend/app-api/src/main/resources/db/migration/V25__workflow_run_count.sql backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/answer/AnswerDraftJpaEntity.java
```

**PASS:** SQL `INT NOT NULL DEFAULT 0`과 JPA int 필드 일치, 기본값 0 설정
**FAIL:** JPA에 workflowRunCount 필드 없음 또는 타입 불일치

### Step 13: V26 product_family + answer_drafts 확장 동기화 확인

**파일:** `V26__chunk_product_family.sql`, `DocumentChunkJpaEntity.java`, `AnswerDraftJpaEntity.java`

**검사:** V26에서 추가된 `product_family VARCHAR(100)` 컬럼이 DocumentChunkJpaEntity에 매핑되고, `sub_question_count INT` / `decomposed_questions TEXT` 컬럼이 AnswerDraftJpaEntity에 매핑되는지 확인.

```bash
grep -n "product_family\|productFamily\|sub_question_count\|subQuestionCount\|decomposed_questions\|decomposedQuestions" backend/app-api/src/main/resources/db/migration/V26__chunk_product_family.sql backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/chunk/DocumentChunkJpaEntity.java backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/answer/AnswerDraftJpaEntity.java
```

**PASS:** SQL 컬럼과 JPA 필드 일치 (product_family→productFamily, sub_question_count→subQuestionCount, decomposed_questions→decomposedQuestions)
**FAIL:** JPA에 대응 필드 없음 또는 타입 불일치

## Output Format (Updated)

| # | 검사 항목 | 결과 | 상세 |
|---|----------|------|------|
| ... | (기존 1~12 항목) | ... | ... |
| 13 | V26 product_family 동기화 | PASS/FAIL | chunk + answer_drafts 컬럼 매핑 |
| 14 | Java 마이그레이션 H2 no-op | PASS/FAIL | BaseJavaMigration + H2 분기 |
| 15 | Java/SQL 버전 번호 중복 없음 | PASS/FAIL | 중복 버전 목록 |
| 16 | V28 이미지 분석 컬럼 동기화 | PASS/FAIL | DocumentMetadataJpaEntity 매핑 |
| 17 | V29 Parent-Child 청킹 동기화 | PASS/FAIL | DocumentChunkJpaEntity 매핑 |
| 18 | V30 enriched_content 동기화 | PASS/FAIL | DocumentChunkJpaEntity 매핑 |
| 19 | V31 RAG 메트릭 테이블 동기화 | PASS/FAIL | RagPipelineMetricEntity 매핑 |
| 20 | V33 서브 질문 컬럼 동기화 | PASS/FAIL | sub_question_count + decomposed_questions 매핑 |

### Step 14: Java 기반 Flyway 마이그레이션 패턴 확인

**파일:** `V27__KoreanTsvectorNormalization.java`

**검사:** Java 기반 마이그레이션이 `BaseJavaMigration`을 상속하고, H2 데이터베이스에서는 no-op(아무 작업 안 함)으로 처리되는지 확인. PostgreSQL 전용 기능(트리거 함수, tsvector 등)을 사용하는 마이그레이션은 H2에서 실행하면 안 됨.

```bash
grep -n "BaseJavaMigration\|H2\|isH2\|getConnection\|DatabaseProduct\|CREATE.*FUNCTION\|TRIGGER" backend/app-api/src/main/java/db/migration/V27__KoreanTsvectorNormalization.java
```

**PASS:** `BaseJavaMigration` 상속 + H2 감지 시 early return (no-op) + PostgreSQL에서만 트리거 함수 생성/교체
**FAIL:** H2 분기 없음 (H2에서 PostgreSQL 전용 SQL 실행 시도) 또는 `BaseJavaMigration` 미상속

### Step 15: Java 마이그레이션 버전 번호 연속성 확인

**파일:** `backend/app-api/src/main/java/db/migration/`, `backend/app-api/src/main/resources/db/migration/`

**검사:** Java 마이그레이션(V{N}__.java)과 SQL 마이그레이션(V{N}__.sql) 간 버전 번호가 중복되지 않는지 확인.

```bash
ls backend/app-api/src/main/resources/db/migration/V*.sql backend/app-api/src/main/java/db/migration/V*.java 2>/dev/null | sed 's/.*\/V\([0-9_]*\)__.*/V\1/' | sort | uniq -d
```

**PASS:** 중복 버전 번호 없음 (SQL과 Java 간 동일 버전 미존재)
**FAIL:** 동일 버전 번호가 .sql과 .java 양쪽에 존재 (Flyway 충돌)

### Step 16: V28 이미지 분석 컬럼 동기화 확인

**파일:** `V28__add_image_analysis_columns.sql`, `DocumentMetadataJpaEntity.java`

**검사:** V28에서 추가된 6개 이미지 분석 컬럼 (image_analysis_type, image_extracted_text, image_visual_description, image_technical_context, image_suggested_query, image_analysis_confidence)이 DocumentMetadataJpaEntity에 올바르게 매핑되는지 확인.

```bash
grep -n "image_analysis_type\|image_extracted_text\|image_visual_description\|image_technical_context\|image_suggested_query\|image_analysis_confidence\|imageAnalysis" backend/app-api/src/main/resources/db/migration/V28__add_image_analysis_columns.sql backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/document/DocumentMetadataJpaEntity.java
```

**PASS:** SQL 6개 컬럼과 JPA 6개 필드 일치 (VARCHAR(20)→length=20, TEXT→columnDefinition="TEXT", DOUBLE PRECISION→Double)
**FAIL:** JPA에 대응 필드 없음 또는 타입 불일치

### Step 17: V29 Parent-Child 청킹 컬럼 동기화 확인

**파일:** `V29__parent_child_chunks.sql`, `DocumentChunkJpaEntity.java`

**검사:** V29에서 추가된 `parent_chunk_id UUID`와 `chunk_level VARCHAR(10) DEFAULT 'CHILD'`가 DocumentChunkJpaEntity에 매핑되는지 확인.

```bash
grep -n "parent_chunk_id\|parentChunkId\|chunk_level\|chunkLevel" backend/app-api/src/main/resources/db/migration/V29__parent_child_chunks.sql backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/chunk/DocumentChunkJpaEntity.java
```

**PASS:** SQL `parent_chunk_id UUID` → JPA `UUID parentChunkId`, SQL `chunk_level VARCHAR(10)` → JPA `String chunkLevel` (length=10, 기본값 "CHILD")
**FAIL:** JPA에 parentChunkId/chunkLevel 필드 없음 또는 기본값 불일치

### Step 18: V30 enriched_content 컬럼 동기화 확인

**파일:** `V30__enriched_content.sql`, `DocumentChunkJpaEntity.java`

**검사:** V30에서 추가된 `context_prefix TEXT`와 `enriched_content TEXT`가 DocumentChunkJpaEntity에 매핑되는지 확인.

```bash
grep -n "context_prefix\|contextPrefix\|enriched_content\|enrichedContent" backend/app-api/src/main/resources/db/migration/V30__enriched_content.sql backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/chunk/DocumentChunkJpaEntity.java
```

**PASS:** SQL TEXT → JPA `columnDefinition = "TEXT"` (contextPrefix, enrichedContent 모두)
**FAIL:** JPA에 필드 없음 또는 columnDefinition = "TEXT" 누락

### Step 19: V31 RAG 메트릭 테이블 동기화 확인

**파일:** `V31__rag_metrics_tracking.sql`, `RagPipelineMetricEntity.java`

**검사:** V31의 `rag_pipeline_metrics` 테이블 정의가 RagPipelineMetricEntity와 일치하는지 확인. 특히 PK 타입(BIGSERIAL vs UUID), timestamp 타입(TIMESTAMP vs TIMESTAMP WITH TIME ZONE)을 검증.

```bash
grep -n "BIGSERIAL\|metric_type\|metric_value\|details\|created_at\|GeneratedValue\|GenerationType" backend/app-api/src/main/resources/db/migration/V31__rag_metrics_tracking.sql backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/metrics/RagPipelineMetricEntity.java
```

**PASS:** SQL 컬럼과 JPA 필드 일치 (BIGSERIAL → GenerationType.IDENTITY, VARCHAR(50) → length=50, DOUBLE PRECISION → double, TEXT → columnDefinition="TEXT")
**FAIL:** JPA에 대응 필드 없음 또는 타입 불일치

**참고:** V31은 프로젝트 표준(UUID PK, TIMESTAMP WITH TIME ZONE)과 다른 패턴을 사용하지만, 메트릭 테이블의 특성상 BIGSERIAL PK와 단순 TIMESTAMP가 허용됨

### Step 20: V33 sub_question_count / decomposed_questions 동기화 확인

**파일:** `V33__add_answer_draft_sub_question_columns.sql`, `AnswerDraftJpaEntity.java`

**검사:** V33에서 추가된 `sub_question_count INT NOT NULL DEFAULT 1`과 `decomposed_questions TEXT` 컬럼이 AnswerDraftJpaEntity에 올바르게 매핑되는지 확인. sub_question_count는 int 타입 + 기본값 1, decomposed_questions는 columnDefinition = "TEXT".

```bash
grep -n "sub_question_count\|subQuestionCount\|decomposed_questions\|decomposedQuestions" backend/app-api/src/main/resources/db/migration/V33__add_answer_draft_sub_question_columns.sql backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/answer/AnswerDraftJpaEntity.java
```

**PASS:** SQL `sub_question_count INT NOT NULL DEFAULT 1` → JPA `int subQuestionCount = 1`, SQL `decomposed_questions TEXT` → JPA `String decomposedQuestions` (columnDefinition = "TEXT")
**FAIL:** JPA에 subQuestionCount/decomposedQuestions 필드 없음 또는 기본값 불일치

## Exceptions

다음은 **위반이 아닙니다**:

1. **ALTER TABLE 구문** — ALTER에는 IF NOT EXISTS가 불필요 (Flyway 버전 관리로 한 번만 실행)
2. **V14의 복합 마이그레이션** — KB 기능 전체를 하나의 마이그레이션에 포함하는 것은 기능 단위 번들링으로 허용
3. **DROP CONSTRAINT IF EXISTS** — FK 제거 시 IF EXISTS 사용은 안전 패턴
4. **Java 마이그레이션의 `.sql` 패턴 미준수** — Java 기반 마이그레이션(V{N}__.java)은 SQL 명명 규칙(V{N}__.sql)과 다르며 이는 정상. Step 2 명명 규칙 검사에서 Java 파일은 제외
5. **Java 마이그레이션의 IF NOT EXISTS 미사용** — Java 코드에서 직접 SQL을 실행하므로 Step 7 IF NOT EXISTS 검사 대상이 아님
6. **V31 BIGSERIAL PK** — 메트릭 테이블은 UUID PK 표준 예외. 대량 삽입/조회 성능을 위해 auto-increment 사용 허용
7. **V31 TIMESTAMP (WITHOUT TIME ZONE)** — 메트릭 테이블의 created_at은 서버 로컬 시간으로 충분하므로 WITH TIME ZONE 미사용 허용
