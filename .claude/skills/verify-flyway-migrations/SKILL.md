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

## Exceptions

다음은 **위반이 아닙니다**:

1. **ALTER TABLE 구문** — ALTER에는 IF NOT EXISTS가 불필요 (Flyway 버전 관리로 한 번만 실행)
2. **V14의 복합 마이그레이션** — KB 기능 전체를 하나의 마이그레이션에 포함하는 것은 기능 단위 번들링으로 허용
3. **DROP CONSTRAINT IF EXISTS** — FK 제거 시 IF EXISTS 사용은 안전 패턴
