---
name: add-flyway-migration
description: Flyway DB 마이그레이션 파일 생성 + JPA 엔티티 동기화. 테이블 추가/수정, 컬럼 추가, 스키마 변경, 마이그레이션 생성 요청 시 사용. DB 변경이 필요한 모든 작업에서 자동 트리거.
---

## Purpose

새 Flyway 마이그레이션 파일을 올바른 버전 번호와 네이밍으로 생성하고,
JPA 엔티티와의 동기화를 보장합니다.

## Workflow

### Step 1: 다음 버전 번호 탐지

마이그레이션 디렉토리에서 최신 버전을 확인합니다:

```bash
ls backend/app-api/src/main/resources/db/migration/ | sort -V | tail -1
```

현재 최신: `V33__add_answer_draft_sub_question_columns.sql`
다음 버전: `V34__...`

### Step 2: 파일 생성

경로: `backend/app-api/src/main/resources/db/migration/V{N}__{snake_case_description}.sql`

네이밍 규칙:
- 접두사: `V{번호}__` (언더스코어 2개)
- 설명: snake_case (예: `add_user_preferences`, `create_notification_table`)
- 확장자: `.sql`

### Step 3: SQL 작성 규칙

프로젝트 표준을 준수합니다:

```sql
-- 테이블 생성
CREATE TABLE IF NOT EXISTS table_name (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    status      VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- 인덱스 추가
CREATE INDEX IF NOT EXISTS idx_table_name_status ON table_name(status);

-- 컬럼 추가
ALTER TABLE table_name ADD COLUMN IF NOT EXISTS new_column VARCHAR(255);
```

| 규칙 | 설명 |
|------|------|
| PK 타입 | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` (예외: 메트릭 테이블은 BIGSERIAL) |
| Timestamp | 반드시 `TIMESTAMP WITH TIME ZONE` (WITH TZ) |
| IF NOT EXISTS | CREATE TABLE, CREATE INDEX, ADD COLUMN에 항상 사용 |
| H2 호환성 | `gen_random_uuid()` 사용 (H2 PostgreSQL 모드 호환) |
| FK 제거 | 외부 테이블 참조 FK는 사용하지 않음 (V16에서 제거한 선례) |

### Step 4: JPA 엔티티 동기화

마이그레이션에 맞는 JPA 엔티티를 업데이트합니다:

```java
// 엔티티 위치
backend/app-api/src/main/java/com/biorad/csrag/infrastructure/persistence/

// 필수 어노테이션
@Entity
@Table(name = "table_name")
public class TableNameJpaEntity {
    @Id
    @Column(name = "id", columnDefinition = "UUID")
    private UUID id;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;  // TIMESTAMP WITH TIME ZONE → OffsetDateTime
}
```

| SQL 타입 | Java 타입 | 어노테이션 |
|----------|----------|-----------|
| UUID | UUID | `@Column(columnDefinition = "UUID")` |
| VARCHAR(N) | String | `@Column(length = N)` |
| TEXT | String | `@Column(columnDefinition = "TEXT")` |
| TIMESTAMP WITH TIME ZONE | OffsetDateTime | `@Column(nullable = false)` |
| BOOLEAN | boolean | `@Column` |
| INTEGER | int / Integer | `@Column` |
| DOUBLE PRECISION | double / Double | `@Column` |
| BIGSERIAL | Long | `@Id @GeneratedValue(strategy = IDENTITY)` |

### Step 5: 검증

`/verify-flyway-migrations` 스킬을 실행하여 일관성을 검증합니다.

## Checklist

- [ ] 버전 번호가 연속적인가?
- [ ] `IF NOT EXISTS` 사용했는가?
- [ ] Timestamp에 `WITH TIME ZONE` 붙였는가?
- [ ] PK가 UUID (또는 메트릭용 BIGSERIAL)인가?
- [ ] JPA 엔티티의 @Column이 마이그레이션과 일치하는가?
- [ ] H2 PostgreSQL 모드에서 호환되는 문법인가?

## Related Files

| File | Purpose |
|------|---------|
| `backend/app-api/src/main/resources/db/migration/` | 마이그레이션 파일 디렉토리 |
| `backend/app-api/src/main/resources/application.yml` | Flyway 설정 (spring.flyway) |
| `backend/app-api/src/main/java/.../infrastructure/persistence/` | JPA 엔티티 디렉토리 |
