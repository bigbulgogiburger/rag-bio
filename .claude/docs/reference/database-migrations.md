# Database & Migrations

> 참조 시점: Flyway 마이그레이션 추가, 스키마 변경, DB 관련 이슈 디버깅 시

## Flyway Migrations

경로: `backend/app-api/src/main/resources/db/migration/`

| 버전 | 설명 |
|------|------|
| V1-V27 | 초기 스키마 (inquiries, documents, chunks, answers, knowledge_base 등) |
| V28 | image_analysis_columns 추가 |
| V29 | parent_child_chunks (chunkLevel, parentChunkId) |
| V30 | enriched_content 컬럼 |
| V31 | rag_metrics_tracking 테이블 |
| V32 | timestamp_tz + answer_draft 동기화 |
| V33 | answer_draft sub_question 컬럼 |
| V34 | kb_chunk product_family backfill |
| V35 | token_tracking 컬럼 (total_prompt_tokens, estimated_cost_usd 등) |
| V36 | semantic_cache + answer_feedback 테이블 |

## 주요 테이블

- `inquiries` — 문의 엔티티
- `documents` — 첨부 문서 메타데이터
- `document_chunks` — 청크 (source_type: INQUIRY/KNOWLEDGE_BASE, chunk_level: PARENT/CHILD)
- `knowledge_base_documents` — KB 문서
- `answer_drafts` — 답변 초안 (상태 머신: DRAFT→REVIEWED→APPROVED→SENT)
- `rag_pipeline_metrics` — 파이프라인 메트릭 (토큰, 비용, 실행시간)
- `semantic_cache` — 의미론적 캐시 (query_embedding_hash, TTL)
- `answer_feedback` — 답변 피드백 (rating, issues)
- `retrieval_evidences` — 검색 증거 감사 로그

## 개발 환경

- H2 (PostgreSQL 호환 모드) — 테스트/로컬 개발
- PostgreSQL 16 — Docker / 프로덕션 (RDS)

## 주의사항

- 마이그레이션 버전은 순차 증가 — 충돌 시 번호 조정 필요
- `add-flyway-migration` 스킬로 자동 생성 권장
- JPA 엔티티와 마이그레이션 동기화 필수 (컬럼명, 타입 일치)
- H2/PostgreSQL 호환성 주의: `TEXT`, `TIMESTAMP WITH TIME ZONE` 사용
