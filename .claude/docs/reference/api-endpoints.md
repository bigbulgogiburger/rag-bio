# API Endpoints

> 참조 시점: API 호출 작성/수정, 프론트엔드-백엔드 연동, 워크플로 이해 시

## Base URL

모든 엔드포인트: `/api/v1/`

## Inquiry

| Method | Path | 설명 |
|--------|------|------|
| GET | `/inquiries` | 문의 목록 (paginated, filters: status, channel, keyword, from, to) |
| POST | `/inquiries` | 문의 생성 |
| GET | `/inquiries/{id}` | 문의 상세 |
| POST | `/inquiries/{id}/documents` | 문서 업로드 (multipart) |
| GET | `/inquiries/{id}/documents` | 문서 목록 |
| GET | `/inquiries/{id}/documents/indexing-status` | 인덱싱 상태 |
| POST | `/inquiries/{id}/documents/indexing/run` | 인덱싱 실행 |
| POST | `/inquiries/{id}/analysis` | 분석 (retrieve + verify) |
| POST | `/inquiries/{id}/answers/draft` | 답변 초안 생성 (전체 오케스트레이션) |
| POST | `/inquiries/{id}/answers/{answerId}/review` | 초안 리뷰 |
| POST | `/inquiries/{id}/answers/{answerId}/approve` | 초안 승인 |
| POST | `/inquiries/{id}/answers/{answerId}/send` | 승인된 초안 발송 |
| GET | `/inquiries/{id}/answers/latest` | 최신 초안 |
| GET | `/inquiries/{id}/answers/history` | 초안 이력 |
| POST | `/inquiries/{id}/answers/{answerId}/feedback` | 답변 피드백 제출 |
| GET | `/inquiries/{id}/answers/{answerId}/feedback` | 피드백 목록 |

## Knowledge Base

| Method | Path | 설명 |
|--------|------|------|
| POST | `/knowledge-base/documents` | KB 문서 업로드 (multipart + metadata) |
| GET | `/knowledge-base/documents` | KB 문서 목록 (filters: category, productFamily, status, keyword) |
| GET | `/knowledge-base/documents/{docId}` | KB 문서 상세 |
| DELETE | `/knowledge-base/documents/{docId}` | KB 문서 삭제 (+ chunks + vectors) |
| POST | `/knowledge-base/documents/{docId}/indexing/run` | 단건 인덱싱 |
| POST | `/knowledge-base/indexing/run` | 미인덱싱 문서 일괄 인덱싱 |
| GET | `/knowledge-base/stats` | KB 통계 |

## Ops

| Method | Path | 설명 |
|--------|------|------|
| GET | `/ops/metrics` | 운영 메트릭 |

## Answer Draft Workflow (State Machine)

```
DRAFT → REVIEWED → APPROVED → SENT
```
- Human-in-the-loop: RBAC via `X-User-Id` / `X-User-Roles` headers
- 각 전환 시 권한 검증 (AGENT, REVIEWER, APPROVER 역할)

## Knowledge Base Integration

- KB 문서와 문의 문서는 `document_chunks` 테이블 공유 (`source_type` 컬럼으로 구분: `INQUIRY` / `KNOWLEDGE_BASE`)
- `ChunkingService.chunkAndStore()`는 `sourceType`, `sourceId` 파라미터 수용
- `VectorStore`의 `upsert()`는 `sourceType` 메타데이터 포함
- 검색 시 `VectorSearchResult.sourceType`으로 출처 구분 → `EvidenceItem`까지 전파

## 주의사항

- API 응답은 영문 enum 사용 (SUPPORTED, CONDITIONAL, REFUTED 등)
- 프론트엔드에서 `labels.ts`로 한국어 변환
- 파일 업로드는 `multipart/form-data` (최대 50MB)
- 인증: JWT 토큰 (Bearer) + `X-User-Id`, `X-User-Roles` 헤더
