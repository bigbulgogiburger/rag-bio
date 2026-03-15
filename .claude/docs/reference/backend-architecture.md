# Backend Architecture

> 참조 시점: 백엔드 구조/패턴 이해, 새 서비스 추가, DDD 레이어 작업 시

## Monorepo Layout

```
backend/
├── app-api/          — 배포 가능한 Spring Boot 앱 (REST, JPA, OpenAI, Flyway, 오케스트레이션)
└── contexts/         — DDD bounded context 모듈
    ├── inquiry-context       — 핵심 도메인 (Inquiry, AskQuestionUseCase, QueryDSL)
    ├── ingestion-context     — (scaffold)
    ├── knowledge-retrieval-context
    ├── verification-context
    ├── response-composition-context
    ├── communication-context
    └── audit-context
```

## Backend Layering (각 context 내부)

```
domain/model/       — Entities, Value Objects (순수 Java, 프레임워크 의존 없음)
domain/repository/  — Repository 인터페이스
application/usecase — Commands, Results, UseCase 인터페이스 + Service 구현
infrastructure/     — JPA 어댑터, 외부 서비스 클라이언트
interfaces/rest/    — Controllers, Request/Response DTO
```

도메인 엔티티는 팩토리 메서드 사용: `Inquiry.create()`, `Inquiry.reconstitute()` — public 생성자 없음.

## app-api 패키지 구조

```
com.biorad.csrag/
├── app/                    — CsRagApplication, AsyncConfig
├── application/ops/        — RagMetricsService
├── common/                 — 공통 유틸
├── infrastructure/
│   ├── openai/             — OpenAiRequestUtils, OpenAiResponseParser, PipelineTraceContext
│   ├── persistence/        — JPA 엔티티/리포지토리 (answer, cache, chunk, document, feedback, metrics)
│   ├── prompt/             — PromptRegistry (src/main/resources/prompts/*.txt 외부화)
│   ├── rag/                — TokenBudgetManager, SemanticCacheService, RagPipelineProperties, RagCostGuardService
│   ├── security/           — RateLimitFilter, JwtTokenProvider
│   └── web/                — WebConfig, CorsConfig
└── interfaces/rest/
    ├── analysis/           — AnalysisService (검색 + 검증 + 증거 품질 게이트)
    ├── answer/             — AnswerController, AnswerComposerService, orchestration/*
    ├── auth/               — AuthController
    ├── chunk/              — ChunkingService, ContextualChunkEnricher
    ├── document/           — DocumentController, DocumentTextExtractor, ImageAnalysisService
    ├── feedback/           — AnswerFeedbackController
    ├── ops/                — OpsController
    ├── search/             — HybridSearchService, Reranking, AdaptiveRetrieval, EvidenceQualityGate
    ├── sse/                — SSE 이벤트 스트리밍
    └── vector/             — EmbeddingService, VectorizingService, QdrantVectorStore, CircuitBreaker
```

## Key Patterns

### Provider-based Routing (Mock/Real 이중 구현)
```java
@ConditionalOnProperty(prefix = "openai", name = "enabled", havingValue = "true")
public class OpenAiComposeStep implements ComposeStep { ... }

@ConditionalOnProperty(prefix = "openai", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DefaultComposeStep implements ComposeStep { ... }
```

### OpenAI 모델 티어링

| 티어 | 환경변수 | 기본값 | 용도 |
|------|---------|--------|------|
| Heavy | `OPENAI_CHAT_MODEL_HEAVY` | `gpt-5-mini` | 답변 작성, 사실 검증 (ComposeStep, CriticAgent) |
| Medium | `OPENAI_CHAT_MODEL_MEDIUM` | `gpt-5-mini` | 검증, 검색 에이전트 (VerifyStep, Reranking) |
| Light | `OPENAI_CHAT_MODEL_LIGHT` | `gpt-5-nano` | 변환, 보강 (HyDE, QueryTranslation) |
| Embedding | `OPENAI_EMBEDDING_MODEL` | `text-embedding-3-large` | 벡터 임베딩 |

### Prompt Registry
프롬프트는 `src/main/resources/prompts/*.txt`에 외부화.
```java
PromptRegistry.get("compose-system");
PromptRegistry.get("hyde-system", Map.of("query", question));
```

### RAG 코어 인프라 (2026-03 추가)
- **TokenBudgetManager**: 문의당 25K 토큰 상한, 필수/선택 단계 구분
- **RagCostGuardService**: 일일 비용 한도, 모델 티어 다운그레이드
- **EvidenceQualityGate**: 5단계 필터 (중복제거→최소점수→문서당제한→다양성→최대수)
- **VectorStoreCircuitBreaker**: 3-state (CLOSED/OPEN/HALF_OPEN), 키워드 폴백
- **SemanticCacheService**: 임베딩 해시 기반 답변 캐시
- **RagPipelineProperties**: 전체 RAG 하이퍼파라미터 application.yml 외부화

## 주의사항

- context 모듈 간 직접 import 금지 — app-api 오케스트레이션 경유
- 새 OpenAI 연동 서비스 추가 시 반드시 Mock 구현도 작성 (`scaffold-openai-service` 스킬 활용)
- JPA 엔티티 변경 시 Flyway 마이그레이션 필수 (`add-flyway-migration` 스킬 활용)
