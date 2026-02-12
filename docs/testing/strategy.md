# Testing Strategy (Draft)

- Backend
  - Domain rule tests by context
  - Use case tests with test doubles for external systems
  - Integration path: upload -> retrieval -> verification -> compose
- Frontend
  - Component tests for key forms and dashboard widgets
  - E2E happy path for inquiry submission and result display
- CI
  - Run lint + tests + build for frontend/backend on PR
