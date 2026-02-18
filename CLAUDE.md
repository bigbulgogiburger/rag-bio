# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# language
한국어로 대답해줘

## Project Overview

Bio-Rad CS Copilot — a RAG-based multi-agent consulting application for Bio-Rad customer service. CS agents submit technical questions with documents (PDF/Word), the system indexes them, runs a Retrieve → Verify → Compose pipeline, and generates answer drafts with citations for human-in-the-loop approval before sending via email/messenger. Additionally, a Knowledge Base module allows pre-registering reference documents (manuals, protocols, FAQ, spec sheets) that are searched alongside inquiry-attached documents during analysis.

## Build & Run Commands

### Docker Compose (Full Stack)

```bash
cd infra
docker compose up -d --build   # Postgres 16 + Backend + Frontend
docker compose down            # Stop all services
docker compose logs -f backend # Follow backend logs
```

Services: postgres(:5432), backend(:8081), frontend(:3001)

### Backend (Spring Boot 3.3.8 / Java 21 / Gradle)

```bash
cd backend
./gradlew build                    # build all modules + run tests
./gradlew :app-api:bootRun         # run API server (localhost:8081)
./gradlew :app-api:test            # run app-api tests only
./gradlew :contexts:inquiry-context:test  # run a single context module's tests
./gradlew :app-api:jacocoTestReport      # generate coverage report
```

### Frontend (Next.js 14 / React 18 / TypeScript)

```bash
cd frontend
npm install
npm run dev       # dev server (localhost:3001)
npm run build     # production build
npm run lint      # ESLint
```

### Environment

```bash
cp .env.example .env   # then fill OPENAI_API_KEY, vector DB keys, etc.
```

Backend reads env vars via `application.yml` placeholders. Key toggles:
- `OPENAI_ENABLED=false` — set `true` for real OpenAI calls; `false` uses mock/fallback services
- `VECTOR_DB_PROVIDER=mock` — options: `mock`, `qdrant`, `pinecone`, `weaviate`
- `SPRING_PROFILES_ACTIVE=docker` — used in docker-compose for PostgreSQL connection

## Architecture

### Monorepo Layout

- `backend/` — Spring Boot multi-module (Gradle), DDD bounded contexts
- `frontend/` — Next.js App Router (`src/app/`, `src/components/`, `src/lib/`)
- `infra/` — docker-compose (Postgres + Backend + Frontend), Dockerfiles in backend/ and frontend/
- `docs/` — architecture, API, testing strategy, workflow reports, sprint backlogs
- `scripts/` — sprint evaluation scripts (Node.js/Bash)

### Backend Module Structure

**`app-api`** — The deployable Spring Boot application. Contains REST controllers, infrastructure adapters (JPA entities/repositories, OpenAI/vector/OCR integrations), Flyway migrations, and the orchestration service.

**`contexts/*`** — DDD bounded context modules. Each has `domain/`, `application/`, `infrastructure/` layers:
- `inquiry-context` — Core domain (Inquiry entity, AskQuestionUseCase, InquirySpecifications). Most mature context with full DDD layers and QueryDSL.
- `ingestion-context`, `knowledge-retrieval-context`, `verification-context`, `response-composition-context`, `communication-context`, `audit-context` — Marker/scaffold modules awaiting implementation.

### Backend Layering (within each context)

```
domain/model/       — Entities, Value Objects (pure Java, no framework deps)
domain/repository/  — Repository interfaces
application/usecase — Commands, Results, UseCase interfaces + Service impls
infrastructure/     — JPA adapters, external service clients
interfaces/rest/    — Controllers, request/response DTOs
```

Domain entities use factory methods (`Inquiry.create()`, `Inquiry.reconstitute()`) — no public constructors.

### Key Backend Patterns

- **Orchestration pipeline**: `AnswerOrchestrationService` chains `RetrieveStep → VerifyStep → ComposeStep` with per-step audit logging
- **Provider-based routing**: Vector store and embedding services are selected at runtime based on `VECTOR_DB_PROVIDER` / `OPENAI_ENABLED` config (e.g., `MockVectorStore` vs `QdrantVectorStore`)
- **Integrated search**: `AnalysisService.retrieve()` searches both inquiry-attached chunks and Knowledge Base chunks, with `sourceType` field distinguishing `INQUIRY` vs `KNOWLEDGE_BASE`
- **JPA Specification**: Dynamic query filters using `InquirySpecifications` and `KnowledgeBaseSpecifications` for paginated list APIs
- **Flyway migrations**: `app-api/src/main/resources/db/migration/V1..V14` — H2 in PostgreSQL compatibility mode for dev, real PostgreSQL via Docker
- **JaCoCo**: Test coverage reports generated on `./gradlew build`, CI prints line/branch coverage

### Frontend Structure

```
src/app/                          — Next.js App Router pages
├── page.tsx                      — Redirect to /dashboard
├── dashboard/page.tsx            — Ops dashboard (metrics + recent 5 inquiries)
├── inquiries/page.tsx            — Inquiry list (filter, pagination, search)
├── inquiries/new/page.tsx        — Create inquiry form
├── inquiries/[id]/page.tsx       — Inquiry detail (4 tabs: info, analysis, answer, history)
├── inquiry/new/page.tsx          — Legacy redirect → /inquiries/new
└── knowledge-base/page.tsx       — Knowledge Base management (CRUD, indexing, stats)

src/components/
├── app-shell-nav.tsx             — Top navigation (4 menus: 대시보드, 문의 목록, 문의 작성, 지식 기반)
├── ui/                           — Design system components (7)
│   ├── Badge.tsx                 — Status badge (variant: info/success/warn/danger/neutral)
│   ├── DataTable.tsx             — Generic table (columns, row click, keyboard nav)
│   ├── Pagination.tsx            — Page navigation + size selector
│   ├── Tabs.tsx                  — Tab switching (arrow keys, ARIA)
│   ├── Toast.tsx                 — Auto-dismiss notifications
│   ├── EmptyState.tsx            — Empty state with CTA
│   ├── FilterBar.tsx             — Filter fields (select/text/date) + search
│   └── index.ts                  — Barrel export
└── inquiry/                      — Inquiry detail tab components (decomposed from inquiry-form.tsx)
    ├── InquiryCreateForm.tsx     — Create form (question + channel + documents)
    ├── InquiryInfoTab.tsx        — Info tab (query details + document list + indexing)
    ├── InquiryAnalysisTab.tsx    — Analysis tab (evidence search + verdict + sourceType badges)
    ├── InquiryAnswerTab.tsx      — Answer tab (draft → review → approve → send workflow)
    ├── InquiryHistoryTab.tsx     — History tab (version history)
    └── index.ts                  — Barrel export

src/lib/
├── api/client.ts                 — Typed API client (inquiry CRUD, document, indexing, analysis, answer workflow, KB CRUD, ops metrics)
└── i18n/labels.ts                — Korean label mappings (verdict, status, doc status, risk flags, tone, channel, error, KB category)
```

- **Design tokens**: `globals.css` uses CSS variables (`--color-*`, `--space-*`, `--font-*`, `--radius-*`, `--shadow-*`, `--transition-*`)
- **Responsive**: Breakpoints at 1279px (tablet) and 767px (mobile)
- **Accessibility**: `focus-visible`, ARIA roles (tablist/tab/tabpanel, table, alert), keyboard navigation
- UI language is Korean (ko)

### API Endpoints (all under `/api/v1/`)

**Inquiry**
- `GET /inquiries` — List inquiries (paginated, filters: status, channel, keyword, from, to)
- `POST /inquiries` — Create inquiry
- `GET /inquiries/{id}` — Get inquiry detail
- `POST /inquiries/{id}/documents` — Upload document (multipart)
- `GET /inquiries/{id}/documents` — List documents
- `GET /inquiries/{id}/documents/indexing-status` — Indexing status
- `POST /inquiries/{id}/documents/indexing/run` — Trigger indexing
- `POST /inquiries/{id}/analysis` — Analyze (retrieve + verify, returns sourceType per evidence)
- `POST /inquiries/{id}/answers/draft` — Generate answer draft (full orchestration)
- `POST /inquiries/{id}/answers/{answerId}/review` — Review draft
- `POST /inquiries/{id}/answers/{answerId}/approve` — Approve draft
- `POST /inquiries/{id}/answers/{answerId}/send` — Send approved draft
- `GET /inquiries/{id}/answers/latest` — Latest draft
- `GET /inquiries/{id}/answers/history` — Draft history

**Knowledge Base**
- `POST /knowledge-base/documents` — Upload KB document (multipart + metadata)
- `GET /knowledge-base/documents` — List KB documents (paginated, filters: category, productFamily, status, keyword)
- `GET /knowledge-base/documents/{docId}` — KB document detail
- `DELETE /knowledge-base/documents/{docId}` — Delete KB document (+ chunks + vectors)
- `POST /knowledge-base/documents/{docId}/indexing/run` — Index single KB document
- `POST /knowledge-base/indexing/run` — Batch index all unindexed KB documents
- `GET /knowledge-base/stats` — KB statistics (totals, by category, by product family)

**Ops**
- `GET /ops/metrics` — Operational metrics

### Answer Draft Workflow State Machine

`DRAFT → REVIEWED → APPROVED → SENT` (human-in-the-loop; RBAC via `X-User-Id` / `X-User-Roles` headers)

### Knowledge Base Integration

- KB documents share `document_chunks` table with inquiry documents, distinguished by `source_type` column (`INQUIRY` / `KNOWLEDGE_BASE`)
- `ChunkingService` has overloaded `chunkAndStore()` accepting `sourceType` and `sourceId` parameters
- `VectorStore` interface includes `deleteByDocumentId()` and `upsert()` with `sourceType` metadata
- `VectorSearchResult` includes `sourceType` field, propagated through `AnalysisService` to `EvidenceItem`

## Development Principles

- **DDD boundaries**: Do not create direct dependencies between context modules. Cross-context communication should go through app-api orchestration or events.
- **TDD**: Write domain/usecase tests first. External dependencies (OpenAI, OCR, mail) must be isolated with test doubles.
- **Commit convention**: `feat|fix|refactor|docs|test|chore|perf|ci(scope): description`
- **No secrets committed**: `.env` is gitignored. Use `.env.example` as template.
- **Build must pass before commit**: backend `./gradlew build`, frontend `npm run build`
- **PRD scope**: Do not expand beyond what's defined in `30_PRD_v2_서비스_고도화.md`
- **Korean labels**: API responses use English enums; frontend converts to Korean at display time via `labels.ts`
- **Design tokens**: Use CSS variables from `globals.css` — no hardcoded colors/sizes in components

## Skills

| 스킬 | 설명 |
|------|------|
| `manage-skills` | 세션 변경사항 분석 및 검증 스킬 유지보수 |
| `verify-implementation` | 등록된 verify 스킬 통합 실행 |
| `verify-kb-indexing` | KB 비동기 인덱싱 파이프라인 검증 |
| `verify-flyway-migrations` | Flyway DB 마이그레이션 일관성 검증 |
| `verify-frontend-ui` | 프론트엔드 UI 컴포넌트 품질 검증 |
| `verify-rag-pipeline` | RAG 파이프라인 (답변 작성 + 분석 + 다운로드) 검증 |
| `verify-inquiry-ui` | 문의 상세 페이지 UI 컴포넌트 검증 |
