# Sprint 5 PRD — 청킹 최적화 + 평가 프레임워크 + 프로덕션 안정화

**브랜치**: `feat/rag-preprocessing-enhancement`
**기간**: Sprint 5 (최종 스프린트)
**작성일**: 2026-03-02
**최신 마이그레이션**: V30 → Sprint 5는 V31부터

---

## 1. 배경 및 목표

### 1.1 이전 세션 미완료 작업

ChunkingService에 `MIN_CHUNK_BEFORE_HEADING_SPLIT = 500` 상수가 선언되었으나, 실제 헤딩 분할 로직에 적용되지 않아 청크 수가 과다(3156개)한 문제가 존재함. 이 작업을 Sprint 5 첫 번째 태스크로 포함.

### 1.2 Sprint 5 목표

1. **청킹 품질 최적화**: 과다 청크 생성 문제 해결
2. **평가 프레임워크 구축**: RAGAS 기반 정량 평가 체계
3. **프롬프트 외부화**: 하드코딩된 프롬프트를 파일 기반으로 관리
4. **운영 메트릭 확장**: Agentic RAG 관련 메트릭 추가
5. **프론트엔드 메트릭 대시보드**: 운영 메트릭 시각화

---

## 2. 태스크 상세

### TASK 5-0: ChunkingService 헤딩 분할 최적화 (미완료 작업)

**우선순위**: P0 (블로커 — 인덱싱 품질에 직결)
**담당**: Backend

**현재 문제**:
```java
// 현재 코드 (line 99-101, 256-258)
if (chunkContent.length() > 0 && isHeading(nextSentence)) {
    break;  // 1자라도 있으면 무조건 헤딩에서 분할 → 단편 청크 다량 생성
}
```

**수정 내용**:
```java
// 수정 후
if (chunkContent.length() >= MIN_CHUNK_BEFORE_HEADING_SPLIT && isHeading(nextSentence)) {
    break;  // 최소 500자 이상일 때만 헤딩에서 분할
}
```

**적용 위치** (2곳):
1. `chunkText(String text, ...)` 메서드 (line ~100)
2. `chunkText(List<PageText> pages, ...)` 메서드 (line ~256)

**HEADING_PATTERN 강화** (선택):
- `[A-Z][A-Z\\s]{2,}$` → 오탐 줄이기 위해 최소 길이 제한 추가: `[A-Z][A-Z\\s]{4,}$`
- 또는 생물학 용어 오탐 방지: `(?!(?:DNA|RNA|PCR|CFX|IVD|ISO|ELISA)\\b)[A-Z][A-Z\\s]{2,}$`

**검증 기준**:
- [ ] 기존 KB 문서 재인덱싱 후 청크 수 3156 → 2000 이하로 감소
- [ ] PARENT 청크 평균 길이 1000자 이상
- [ ] 기존 단위 테스트 통과

---

### TASK 5-1: RAGAS 기반 평가 파이프라인

**우선순위**: P1
**담당**: Backend (evaluation 패키지)

**신규 파일**:
```
backend/app-api/src/test/java/com/biorad/csrag/evaluation/
├── RagasEvaluator.java        — 평가 엔진 (4개 메트릭 계산)
├── GoldenDataset.java         — 골든 데이터셋 로더
├── EvaluationReport.java      — 평가 결과 DTO
└── RagRegressionTest.java     — 회귀 테스트 (품질 게이트)

backend/app-api/src/test/resources/
└── golden-dataset.json        — 골든 데이터셋 (최소 10개 케이스)
```

**평가 메트릭**:
| 메트릭 | 설명 | 최소 기준 |
|--------|------|----------|
| Faithfulness | 답변이 검색된 컨텍스트에 근거하는지 | ≥ 0.80 |
| Answer Relevancy | 답변이 질문에 적절한지 | ≥ 0.80 |
| Context Precision | 검색 컨텍스트의 정밀도 | ≥ 0.75 |
| Context Recall | 필요한 컨텍스트가 검색되었는지 | ≥ 0.85 |

**골든 데이터셋 스키마**:
```json
{
  "cases": [
    {
      "id": "case-001",
      "question": "CFX96 Touch의 형광 채널 보정 방법은?",
      "expectedAnswer": "Fluorescence calibration wizard에서...",
      "relevantDocuments": ["cfx96-manual.pdf"],
      "groundTruthFacts": [
        "형광 보정은 Calibration 메뉴에서 수행",
        "최소 3회 반복 측정 권장"
      ]
    }
  ]
}
```

**구현 방식**: OpenAI GPT-4.1-mini를 사용하여 LLM-as-Judge 패턴으로 각 메트릭 산출

**검증 기준**:
- [ ] 골든 데이터셋 10개 이상 케이스
- [ ] 4개 메트릭 산출 후 EvaluationReport 정상 생성
- [ ] `./gradlew :app-api:test --tests "*RagRegressionTest*"` 통과

---

### TASK 5-2: 프롬프트 외부화 시스템

**우선순위**: P1
**담당**: Backend

**현재 문제**: 12개 서비스 클래스에 총 300줄+ 시스템 프롬프트가 하드코딩됨

**신규 파일**:
```
backend/app-api/src/main/resources/prompts/
├── compose-system.txt         — 답변 작성 프롬프트
├── verify-system.txt          — 검증 프롬프트
├── critic-system.txt          — Critic Agent 프롬프트
├── hyde-system.txt            — HyDE 변환 프롬프트
├── adaptive-search.txt        — 적응형 검색 프롬프트
├── multihop-system.txt        — Multi-Hop 프롬프트
├── reranking-system.txt       — 리랭킹 프롬프트
├── contextual-enrichment.txt  — 컨텍스트 보강 프롬프트
├── query-translation.txt      — 쿼리 번역 프롬프트
├── image-analysis.txt         — 이미지 분석 프롬프트
├── metadata-analysis.txt      — 메타데이터 분석 프롬프트
└── review-agent.txt           — 리뷰 에이전트 프롬프트

backend/app-api/src/main/java/com/biorad/csrag/infrastructure/prompt/
└── PromptRegistry.java        — 클래스패스에서 프롬프트 로드
```

**PromptRegistry 설계**:
```java
@Component
public class PromptRegistry {
    private final Map<String, String> prompts = new ConcurrentHashMap<>();

    @PostConstruct
    void loadPrompts() { /* classpath:prompts/*.txt 로드 */ }

    public String get(String name) { return prompts.get(name); }
    public String get(String name, Map<String, String> variables) { /* 변수 치환 */ }
}
```

**수정 대상 서비스 (12개)**:
1. OpenAiComposeStep
2. OpenAiVerifyStep
3. OpenAiCriticAgentService
4. OpenAiHydeQueryTransformer
5. OpenAiAdaptiveRetrievalAgent
6. OpenAiMultiHopRetriever
7. OpenAiRerankingService
8. OpenAiContextualChunkEnricher
9. OpenAiQueryTranslationService
10. OpenAiImageAnalysisService
11. DocumentMetadataAnalyzer
12. ReviewAgentService

**검증 기준**:
- [ ] 모든 12개 서비스에서 하드코딩된 프롬프트 제거
- [ ] PromptRegistry를 통한 프롬프트 로드 확인
- [ ] 프롬프트 파일 변경 시 재배포 필요 (classpath 기반이므로 정상)
- [ ] 기존 기능 테스트 통과 (답변 생성, 분석, 인덱싱)

---

### TASK 5-3: 운영 메트릭 확장

**우선순위**: P2
**담당**: Backend + Frontend

**Backend 수정**:
- `OpsMetricsController.java` — 신규 메트릭 엔드포인트
- `OpsMetricsService.java` (신규) — 메트릭 집계 로직

**추가 메트릭**:
```json
{
  "ragMetrics": {
    "avgSearchScore": 0.72,
    "avgRerankImprovement": 0.15,
    "adaptiveRetryRate": 0.23,
    "hydeUsageRate": 0.85,
    "criticRevisionRate": 0.12,
    "multiHopActivationRate": 0.31,
    "avgChunksPerDocument": 45.2,
    "avgParentChunkLength": 1500
  },
  "pipelineMetrics": {
    "avgAnswerGenerationTimeMs": 15000,
    "avgIndexingTimeMs": 8000,
    "imageAnalysisRate": 0.05
  }
}
```

**V31 마이그레이션** (필요 시):
```sql
-- V31__rag_metrics_tracking.sql
CREATE TABLE rag_pipeline_metrics (
    id BIGSERIAL PRIMARY KEY,
    inquiry_id BIGINT REFERENCES inquiry(id),
    metric_type VARCHAR(50) NOT NULL,
    metric_value DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_rpm_inquiry ON rag_pipeline_metrics(inquiry_id);
CREATE INDEX idx_rpm_type ON rag_pipeline_metrics(metric_type);
```

**Frontend 수정**:
- `dashboard/page.tsx` — RAG 메트릭 카드 섹션 추가
- `src/components/ui/MetricCard.tsx` (신규) — 메트릭 시각화 컴포넌트

**검증 기준**:
- [ ] `GET /api/v1/ops/metrics` 응답에 ragMetrics 포함
- [ ] 대시보드에 RAG 메트릭 카드 표시
- [ ] 파이프라인 실행 후 메트릭 정상 집계

---

### TASK 5-4: Frontend 운영 대시보드 고도화

**우선순위**: P2
**담당**: Frontend

**수정 파일**:
- `dashboard/page.tsx` — 레이아웃 재구성, 메트릭 카드 추가
- `src/lib/api/client.ts` — ragMetrics 타입 추가

**UI 구성**:
```
┌─────────────────────────────────────────┐
│  대시보드                                │
├───────────┬───────────┬─────────────────┤
│ 총 문의    │ 처리 중    │ 완료            │
│    45     │    8      │    37           │
├───────────┴───────────┴─────────────────┤
│  RAG 파이프라인 메트릭                    │
├───────────┬───────────┬─────────────────┤
│ 검색 정확도 │ 리랭킹 개선 │ Critic 수정률  │
│  72%      │  +15%     │   12%          │
├───────────┼───────────┼─────────────────┤
│ HyDE 사용률│ Multi-Hop │ 적응형 재시도    │
│  85%      │  31%      │   23%          │
├───────────┴───────────┴─────────────────┤
│  최근 문의 (5건)                         │
│  ...                                    │
└─────────────────────────────────────────┘
```

**검증 기준**:
- [ ] 대시보드에 RAG 메트릭 카드 6개 표시
- [ ] 모바일 반응형 (2열 → 1열)
- [ ] 메트릭 로딩 실패 시 graceful fallback

---

## 3. 태스크 의존성 맵

```
TASK 5-0 (청킹 최적화)     → 독립 (블로커 없음)
TASK 5-1 (RAGAS 평가)      → 독립 (테스트 패키지)
TASK 5-2 (프롬프트 외부화)  → 독립 (리팩터링)
TASK 5-3 (메트릭 Backend)   → 독립
TASK 5-4 (메트릭 Frontend)  → TASK 5-3에 의존 (API 응답 스키마)
```

**병렬 가능**: TASK 5-0, 5-1, 5-2, 5-3은 완전 독립 → 동시 진행 가능
**순차 필수**: TASK 5-4는 TASK 5-3 완료 후 진행

---

## 4. 팀 구성 (Agent Team)

| 역할 | 담당 태스크 | Agent Type |
|------|-----------|------------|
| **team-lead** | 전체 조율, 코드 리뷰, 병합 | orchestrator |
| **chunking-optimizer** | TASK 5-0: ChunkingService 최적화 | general-purpose |
| **eval-builder** | TASK 5-1: RAGAS 평가 프레임워크 | general-purpose |
| **prompt-engineer** | TASK 5-2: 프롬프트 외부화 시스템 | general-purpose |
| **metrics-backend** | TASK 5-3: 메트릭 Backend 확장 | general-purpose |
| **metrics-frontend** | TASK 5-4: 메트릭 Frontend 대시보드 | general-purpose |

---

## 5. 완료 기준

### Must Have (P0/P1)
- [ ] ChunkingService 헤딩 분할 최적화 적용 완료
- [ ] RAGAS 평가 프레임워크 구축 (골든 데이터셋 10+개)
- [ ] 프롬프트 12개 서비스 외부화 완료

### Should Have (P2)
- [ ] 운영 메트릭 Backend API 확장
- [ ] Frontend 대시보드 메트릭 카드

### 검증
- [ ] `./gradlew build` 성공
- [ ] `npm run build` 성공
- [ ] Docker Compose 전체 스택 정상 기동
