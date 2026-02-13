# Bio-Rad CS Copilot Monorepo

Production-oriented monorepo scaffold for a RAG-based multi-agent consulting application defined in `PRD_biorad_cs_rag_agent.md`.

## Repository Layout

- `frontend/`: React + Next.js App Router UI
- `backend/`: Spring Boot 3.3.8 multi-module backend (DDD-style context modules)
- `docs/`: architecture, API contract placeholders, testing strategy docs
- `infra/`: infrastructure placeholders for container/orchestration/IaC

## Architecture Overview

- Frontend (`Next.js`)
- Backend API (`Spring Boot 3.3.8`)
- Bounded contexts (DDD):
  - `ingestion-context`
  - `inquiry-context`
  - `knowledge-retrieval-context`
  - `verification-context`
  - `response-composition-context`
  - `communication-context`
  - `audit-context`
- Persistence and query:
  - `JPA`
  - `QueryDSL` (dependency scaffolding in `inquiry-context`)

## Quick Start

### Prerequisites

- `Node.js` 20+
- `npm` 10+
- `JDK` 21
- `Gradle` 8+ (or add Gradle wrapper next)

### Run Frontend

```bash
cd frontend
npm install
npm run dev
```

App runs by default on `http://localhost:3000`.

### Run Backend

```bash
cd backend
gradle :app-api:bootRun
```

API runs by default on `http://localhost:8080`.

### Smoke API Call

```bash
curl -X POST http://localhost:8080/api/v1/inquiries \
  -H 'Content-Type: application/json' \
  -d '{"question":"Can reagent X be used at 4C overnight?","customerChannel":"email"}'
```

## Current Scope

This scaffold includes:

- backend multi-module layout with context modules
- sample DDD-oriented classes in `inquiry-context`:
  - domain entity
  - repository interface
  - application use case contract
  - JPA adapter skeleton
- one REST controller in `app-api`
- frontend pages:
  - `/dashboard`
  - `/inquiry/new`
- frontend API client layer (`src/lib/api/client.ts`)

## TODO (Next Milestones)

- Add Gradle wrapper (`gradlew`) for reproducible backend runs
- Define OpenAPI contract under `docs/api/openapi.yaml`
- Implement ingestion pipeline (PDF/Word parsing + OCR) in `ingestion-context`
- Implement retrieval/verifier/composer orchestration use cases
- Add DB migration tooling (Flyway/Liquibase) and production DB profile
- Add test suites:
  - backend unit/integration tests (TDD flow)
  - frontend component/e2e tests
- Add CI/CD and deployment manifests in `infra/`
- Add auth/RBAC, audit enrichment, and observability

## Environment Setup (.env)

```bash
cp .env.example .env
```

Then fill values in `.env` (do not commit real secrets):

- `OPENAI_API_KEY`
- optional vector DB keys (`QDRANT_*`, `PINECONE_*`, `WEAVIATE_*`)

Backend reads these via `application.yml` placeholders.

## Docker Compose

Postgres compose file is at `infra/docker-compose.yml` and reads `../.env`.

```bash
cd infra
docker compose up -d
```

## Notes

- No secrets are committed.
- Environment-specific values should be provided via env vars at runtime.
